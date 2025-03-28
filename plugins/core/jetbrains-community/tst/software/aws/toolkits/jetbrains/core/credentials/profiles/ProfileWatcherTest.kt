// Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.core.credentials.profiles

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.AssumptionViolatedException
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.opentest4j.AssertionFailedError
import software.aws.toolkits.core.rules.SystemPropertyHelper
import software.aws.toolkits.jetbrains.utils.spinUntil
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ProfileWatcherTest {
    companion object {
        @ClassRule
        @JvmField
        val disposableRule = DisposableRule()
    }

    @Rule
    @JvmField
    val application = ApplicationRule()

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    @Rule
    @JvmField
    val systemPropertyHelper = SystemPropertyHelper()

    private lateinit var awsFolder: File
    private lateinit var profileFile: File
    private lateinit var credentialsFile: File

    @Before
    fun setUp() {
        awsFolder = File(temporaryFolder.root, ".aws")
        profileFile = File(awsFolder, "config")
        credentialsFile = File(awsFolder, "credentials")

        System.getProperties().setProperty("aws.configFile", profileFile.absolutePath)
        System.getProperties().setProperty("aws.sharedCredentialsFile", credentialsFile.absolutePath)

        try {
            assertFileChange {
                profileFile.parentFile.mkdirs()
                profileFile.writeText("Test")
                profileFile.parentFile.deleteRecursively()
            }
        } catch (e: Throwable) {
            if (e is AssertionFailedError) {
                // suppress
            } else if (e.cause is IOException) {
                throw AssumptionViolatedException("native file watcher is not executable; possibly an issue with intellij-platform-gradle-plugin", e)
            } else {
                throw e
            }
        }
    }

    @Test
    fun `watcher is notified on creation`() {
        profileFile.parentFile.mkdirs()

        assertFileChange {
            profileFile.writeText("Test")
        }
    }

    @Test
    fun `watcher is notified on edit`() {
        profileFile.parentFile.mkdirs()
        profileFile.writeText("Test")

        assertThat(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(profileFile)).isNotNull

        assertFileChange {
            profileFile.writeText("Test2")
        }
    }

    @Test
    fun `watcher is notified on deletion`() {
        profileFile.parentFile.mkdirs()
        profileFile.writeText("Test")

        assertThat(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(profileFile)).isNotNull

        assertFileChange {
            profileFile.delete()
        }

        assertThat(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(profileFile)).isNull()
    }

    /**
     * These tests are complicated and reaching into some low level systems inside of the IDE to replicate how it works due stuff is disabled in unit test mode.
     *
     * First, FileWatcher (fsnotify[.exe]) that notifies the IDE that files are dirty is not ran in unit test mode. We start/stop it manually so that we can
     * validate external edits to the profile file is handled.
     *
     * Second, the system that marks VFS files dirty from the FileWatcher is only configured to run if FileWatcher is running in the constructor.
     * See com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl constructor. Due to that, we schedule a manual VFS refresh to recheck all the files
     * marked dirty.
     */
    private fun assertFileChange(block: () -> Unit) {
        val fileWatcher = (LocalFileSystem.getInstance() as LocalFileSystemImpl).fileWatcher
        Disposer.register(
            disposableRule.disposable
        ) {
            fileWatcher.shutdown()

            spinUntil(Duration.ofSeconds(10)) {
                !fileWatcher.isOperational
            }
        }

        val watcherTriggered = CountDownLatch(1)
        fileWatcher.startup {
            // Contains due to /private/ vs /
            if (it.contains(awsFolder.absolutePath)) {
                watcherTriggered.countDown()
            }
        }

        spinUntil(Duration.ofSeconds(10)) {
            fileWatcher.isOperational
        }

        val sut = DefaultProfileWatcher()
        Disposer.register(disposableRule.disposable, sut)

        spinUntil(Duration.ofSeconds(10)) {
            !fileWatcher.isSettingRoots
        }

        val updateCalled = Ref.create(false)
        sut.addListener { updateCalled.set(true) }

        block()

        // Wait for fsnotify to see the change
        assertThat(watcherTriggered.await(5, TimeUnit.SECONDS)).describedAs("FileWatcher is triggered").isTrue

        val refreshComplete = CountDownLatch(1)
        RefreshQueue.getInstance().refresh(true, true, { refreshComplete.countDown() }, *ManagingFS.getInstance().localRoots)

        // Wait for refresh to complete
        refreshComplete.await()

        // The update is triggered asynchronously, so this does not mean it's done, especially when the system is under high load
        // Since we are compiling ultimate/rider while running this test it can fail easily. 2000 is since we are dealing with the filesystem
        // so, pick a high arbitrary value
        if (updateCalled.get() == false) {
            Thread.sleep(2000)
        }
        assertThat(updateCalled.get()).describedAs("ProfileWatcher is triggered").isTrue
    }
}
