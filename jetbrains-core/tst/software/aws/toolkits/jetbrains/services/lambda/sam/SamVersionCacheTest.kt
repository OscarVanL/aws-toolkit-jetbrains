// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.sam

import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.all
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import software.aws.toolkits.jetbrains.utils.rules.HeavyJavaCodeInsightTestFixtureRule
import software.aws.toolkits.resources.message

class SamVersionCacheTest {

    @JvmField
    @Rule
    val expectedException: ExpectedException = ExpectedException.none()

    @Rule
    @JvmField
    val projectRule = HeavyJavaCodeInsightTestFixtureRule()

    @After
    fun tearDown() {
        SamVersionCache.testOnlyGetRequestCache().clear()
    }

    @Test
    fun invalidSamExecutablePath() {
        val invalidPath = "invalid_path"

        expectedException.expect(IllegalStateException::class.java)
        expectedException.expectMessage(message("general.file_not_found", invalidPath))

        SamVersionCache.evaluateBlocking(invalidPath)
    }

    @Test
    fun samCliMinVersion() {
        val samPath = SamCommonTestUtils.makeATestSam(SamCommonTestUtils.getMinVersionAsJson()).toString()
        val samVersion = SamVersionCache.evaluateBlocking(samPath)
        assertEquals("Mismatch SAM executable version", samVersion, SamCommon.expectedSamMinVersion)
    }

    @Test
    fun samCliInvalidVersion() {
        val version = "0.0.a"

        expectedException.expect(IllegalStateException::class.java)
        expectedException.expectMessage(message("sam.executable.version_parse_error", version))

        val samPath = SamCommonTestUtils.makeATestSam(SamCommonTestUtils.getVersionAsJson(version)).toString()
        SamVersionCache.evaluateBlocking(samPath)
    }

    @Test
    fun errorCode_RandomError() {
        val message = "No such file or directory"

        expectedException.expect(IllegalStateException::class.java)
        expectedException.expectMessage(message)

        val samPath = SamCommonTestUtils.makeATestSam(message, exitCode = 1).toString()
        SamVersionCache.evaluateBlocking(samPath)
    }

    @Test
    fun errorCode_InvalidOption() {
        val message = "Error: no such option: --some_option"

        expectedException.expect(IllegalStateException::class.java)
        expectedException.expectMessage(message("sam.executable.unexpected_output", message))

        val samPath = SamCommonTestUtils.makeATestSam(message, exitCode = 1).toString()
        SamVersionCache.evaluateBlocking(samPath)
    }

    @Test
    fun successExecution_EmptyOutput() {
        val message = ""

        expectedException.expect(IllegalStateException::class.java)
        expectedException.expectMessage(message("sam.executable.empty_info"))

        val samPath = SamCommonTestUtils.makeATestSam(message).toString()
        SamVersionCache.evaluateBlocking(samPath)
    }

    private fun waitAll(promises: Collection<Promise<*>>) {
        val all = promises.all(null, ignoreErrors = true)
        all.blockingGet(3000)
    }
}
