package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path

class CorePoliciesTest {

    @Test
    fun carrierName_normalizesOuterWhitespace() {
        assertEquals("中国联通", IdentityInputPolicy.normalize("  中国联通  "))
    }

    @Test
    fun carrierName_countsUnicodeCodePointsInsteadOfUtf16Units() {
        assertNull(
            IdentityInputPolicy.carrierNameError("📶".repeat(32)),
        )
        assertEquals(
            IdentityInputPolicy.Error.TOO_LONG,
            IdentityInputPolicy.carrierNameError("📶".repeat(33)),
        )
    }

    @Test
    fun identityInput_rejectsControlCharacters() {
        assertEquals(
            IdentityInputPolicy.Error.CONTROL_CHARACTER,
            IdentityInputPolicy.carrierNameError("中国\n联通"),
        )
        assertEquals(
            IdentityInputPolicy.Error.CONTROL_CHARACTER,
            IdentityInputPolicy.imsUserAgentError("OneIms\u0000UA"),
        )
    }

    @Test
    fun operationErrors_unwrapsReflectionException() {
        val error = InvocationTargetException(
            SecurityException("android.permission.WRITE_SECURE_SETTINGS"),
        )

        assertEquals(
            "SecurityException: android.permission.WRITE_SECURE_SETTINGS",
            OperationErrors.describe(error),
        )
    }

    @Test
    fun operationErrors_neverReturnsBlankMessage() {
        assertEquals("IllegalStateException", OperationErrors.describe(IllegalStateException()))
    }

    @Test
    fun brokerOperations_verifyTheirRequiredDelegatedPermission() {
        assertEquals(
            setOf("android.permission.MODIFY_PHONE_STATE"),
            BrokerProtocol.requiredPermissions(BrokerProtocol.OP_OVERRIDE_CONFIG),
        )
        assertEquals(
            setOf("android.permission.WRITE_SECURE_SETTINGS"),
            BrokerProtocol.requiredPermissions(BrokerProtocol.OP_WRITE_GLOBAL_INT),
        )
        assertEquals(
            setOf("android.permission.WRITE_APN_SETTINGS"),
            BrokerProtocol.requiredPermissions(BrokerProtocol.OP_INSERT_IMS_APN),
        )
        assertEquals(emptySet<String>(), BrokerProtocol.requiredPermissions("unknown"))
    }

    @Test
    fun brokerExecutionException_distinguishesPreflightFromPossibleWrite() {
        val preflight = BrokerExecutionException("delegate failed", operationStarted = false)
        val started = BrokerExecutionException("readback timed out", operationStarted = true)

        assertFalse(preflight.operationStarted)
        assertTrue(started.operationStarted)
    }

    @Test
    fun operationFeedback_prioritizesPermissionRootCauseOverRollbackWording() {
        val screenshotError = """
            写入失败：override_config: RemoteException:
            AccessibilityManagerService.registerUiTestAutomationService
            Access denied, requires android.permission.RETRIEVE_WINDOW_CONTENT
            自动回滚也失败
        """.trimIndent()

        assertEquals(
            OperationFeedbackKind.PERMISSION_DELEGATION_FAILED,
            OperationFeedbackPolicy.classify(screenshotError),
        )
        assertEquals(
            OperationFeedbackKind.PERMISSION_DELEGATION_FAILED,
            OperationFeedbackPolicy.classify("配置未写入：Shizuku delegate failed"),
        )
    }

    @Test
    fun operationFeedback_keepsShortMessagesAndCollapsesLongDetails() {
        assertEquals(
            OperationFeedbackKind.INLINE,
            OperationFeedbackPolicy.classify("配置已下发，基本通信正常"),
        )
        assertEquals(
            OperationFeedbackKind.LONG_FAILURE,
            OperationFeedbackPolicy.classify("Operation failed\nRemoteException\nat Binder"),
        )
        assertEquals(
            OperationFeedbackKind.LONG_RESULT,
            OperationFeedbackPolicy.classify("IMS 状态\nVoLTE: true\nVoWiFi: true"),
        )
    }

    @Test
    fun imsApnPolicy_splitsOnlyAnExactMixedImsToken() {
        assertEquals(
            "default,supl",
            ImsApnTypePolicy.withoutIms(" default, SUPL, ims,default "),
        )
        assertNull(ImsApnTypePolicy.withoutIms("ims"))
        assertNull(ImsApnTypePolicy.withoutIms("default,xcapims"))
    }

    @Test
    fun imsApnPolicy_recognizesDedicatedImsCaseInsensitively() {
        assertTrue(ImsApnTypePolicy.isDedicatedIms(" IMS "))
        assertFalse(ImsApnTypePolicy.isDedicatedIms("default,ims"))
        assertFalse(ImsApnTypePolicy.isDedicatedIms(null))
    }

    @Test
    fun imsRegistration_unknownKeepsReadbackFailureDistinct() {
        val unknown = SystemApiBroker.ImsRegInfo.unknown()

        assertFalse(unknown.registered)
        assertFalse(unknown.querySucceeded)
        assertEquals(-1, unknown.radioTech)
    }

    @Test
    fun apnCatalogTsv_decodesEscapedFieldsWithoutLosingColumns() {
        val encoded = MutableList(25) { "" }.apply {
            this[0] = "CN"
            this[1] = "LineageOS"
            this[2] = "Carrier\\tName"
            this[5] = "ims"
            this[6] = "ims"
            this[9] = "user\\\\name"
        }.joinToString("\t")

        val decoded = ApnCatalogTsv.decodeLine(encoded)

        assertEquals(25, decoded.size)
        assertEquals("Carrier\tName", decoded[2])
        assertEquals("user\\name", decoded[9])
    }

    @Test
    fun apnCatalogPolicy_appliesOnlyDedicatedEnabledImsProfiles() {
        assertTrue(apnEntry(types = " IMS ").isSafeImsTemplate)
        assertFalse(apnEntry(types = "default,ims").isSafeImsTemplate)
        assertFalse(apnEntry(types = "ims", carrierEnabled = false).isSafeImsTemplate)
    }

    @Test
    fun apnCatalogPolicy_matchesNumericOrCanonicalCarrierId() {
        val numeric = apnEntry(mcc = "460", mnc = "03", carrierId = 1897)
        assertTrue(ApnCatalogPolicy.matchesCurrentSim(numeric, "460", "03", null))
        assertTrue(ApnCatalogPolicy.matchesCurrentSim(numeric, "001", "01", 1897))
        assertFalse(ApnCatalogPolicy.matchesCurrentSim(numeric, "460", "11", 1435))
    }

    @Test
    fun imsApnTemplate_usesOfflineValuesAndSafeDefaults() {
        val profile = apnEntry(
            carrier = "China Telecom IMS",
            apn = "ims.ct",
            protocol = "IPV6",
            roamingProtocol = "",
        )
        val selected = ImsApnTemplatePolicy.resolve(profile)
        val fallback = ImsApnTemplatePolicy.resolve(null)

        assertEquals("China Telecom IMS", selected.name)
        assertEquals("ims.ct", selected.apn)
        assertEquals("IPV6", selected.roamingProtocol)
        assertEquals("IMS", fallback.name)
        assertEquals("ims", fallback.apn)
        assertEquals("IPV4V6", fallback.protocol)
    }

    @Test
    fun bundledApnCatalog_hasBroadValidatedOfflineCoverage() {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        val catalog = listOf(
            workingDirectory.resolve("app/src/main/assets/apn_catalog.tsv"),
            workingDirectory.resolve("src/main/assets/apn_catalog.tsv"),
        ).firstOrNull(Files::isRegularFile)
        checkNotNull(catalog) { "Bundled APN catalog not found from $workingDirectory" }

        val logicalKeys = mutableSetOf<String>()
        val plmns = mutableSetOf<String>()
        var records = 0
        var imsRecords = 0
        Files.newBufferedReader(catalog).useLines { lines ->
            val iterator = lines.iterator()
            assertTrue(iterator.hasNext())
            assertEquals(ApnCatalogTsv.header, iterator.next())
            iterator.forEach { line ->
                if (line.isBlank()) return@forEach
                val fields = ApnCatalogTsv.decodeLine(line)
                val mcc = fields[3]
                val mnc = fields[4]
                val carrierId = fields[17]
                assertTrue(
                    (mcc.length == 3 && mcc.all(Char::isDigit)) ||
                        (carrierId.isNotEmpty() && carrierId.all(Char::isDigit)),
                )
                val normalizedTypes = ApnCatalogPolicy.normalizeTypes(fields[6])
                    .sorted()
                    .joinToString(",")
                val key = listOf(
                    mcc,
                    mnc,
                    carrierId,
                    fields[5].lowercase(),
                    normalizedTypes,
                    fields[18].lowercase(),
                    fields[19].lowercase(),
                ).joinToString("\u001F")
                assertTrue("Duplicate APN logical key: $key", logicalKeys.add(key))
                if (mcc.isNotEmpty()) plmns += "$mcc:$mnc"
                if ("ims" in normalizedTypes.split(',')) imsRecords++
                records++
            }
        }

        assertTrue(records >= 6_500)
        assertTrue(imsRecords >= 600)
        assertTrue(plmns.size >= 1_400)
    }

    @Test
    fun brokerCompletion_finishesInstrumentationBeforePublishingResult() {
        val events = mutableListOf<String>()

        val error = BrokerCompletionOrder.finishBeforePublishing(
            finishInstrumentation = { events += "finish" },
            publishResult = { events += "publish" },
        )

        assertNull(error)
        assertEquals(listOf("finish", "publish"), events)
    }

    @Test
    fun brokerCompletion_stillPublishesWhenFinishFails() {
        val finishError = IllegalStateException("finish failed")
        var published = false

        val returnedError = BrokerCompletionOrder.finishBeforePublishing(
            finishInstrumentation = { throw finishError },
            publishResult = { published = true },
        )

        assertEquals(finishError, returnedError)
        assertTrue(published)
    }

    @Test
    fun brokerResultBus_releasesRegisteredCallerWithPublishedResult() {
        val requestId = "core-policies-test"
        val expected = BrokerResult(
            success = true,
            message = "ok",
            operationStarted = true,
        )
        BrokerResultBus.register(requestId)
        try {
            BrokerResultBus.complete(requestId, expected)
            assertEquals(expected, BrokerResultBus.await(requestId, timeoutMs = 100))
        } finally {
            BrokerResultBus.remove(requestId)
        }
    }

    @Test
    fun expertConfigPolicy_acceptsExistingStyleKeysAndRejectsCommunicationRedLines() {
        assertTrue(ExpertConfigPolicy.validateKey("show_ims_registration_status_bool"))
        assertTrue(ExpertConfigPolicy.validateKey("Carrier_VoLTE_Available_Bool"))
        assertFalse(ExpertConfigPolicy.validateKey(""))
        assertFalse(ExpertConfigPolicy.validateKey("preferred_network_mode_int"))
        assertFalse(ExpertConfigPolicy.validateKey("emergency_sms_mode_bool"))
        assertFalse(ExpertConfigPolicy.validateKey("../carrier_volte_available_bool"))
        CarrierConfigKeys.specializedManagerKeys.forEach { key ->
            assertFalse(
                "Specialized key must not bypass its owner: $key",
                ExpertConfigPolicy.validateKey(key),
            )
        }
    }

    @Test
    fun expertConfigPolicy_rejectsControlCharactersAndOversizedValues() {
        assertTrue(ExpertConfigPolicy.validateValue("true"))
        assertTrue(ExpertConfigPolicy.validateValue("1, 2, 3"))
        assertFalse(ExpertConfigPolicy.validateValue("line1\nline2"))
        assertFalse(ExpertConfigPolicy.validateValue("x".repeat(2_049)))
    }

    @Test
    fun apnCatalogDatabaseVersion_tracksCurrentBundledAssetGeneration() {
        assertTrue(APN_CATALOG_DATABASE_VERSION >= 2)
    }

    @Test
    fun reapplyTrigger_restoresKnownValuesAndSafelyFallsBack() {
        assertEquals(
            ReapplyTrigger.IMS_NOT_REGISTERED,
            ReapplyTrigger.fromStored("ims_not_registered"),
        )
        assertEquals(ReapplyTrigger.MANUAL, ReapplyTrigger.fromStored("future_trigger"))
        assertEquals(ReapplyTrigger.MANUAL, ReapplyTrigger.fromStored(null))
    }

    private fun apnEntry(
        carrier: String = "Carrier IMS",
        mcc: String = "460",
        mnc: String = "03",
        carrierId: Int? = null,
        apn: String = "ims",
        types: String = "ims",
        protocol: String = "IPV4V6",
        roamingProtocol: String = "IPV4V6",
        carrierEnabled: Boolean = true,
    ) = ApnCatalogEntry(
        id = 1,
        countryCode = "CN",
        source = "LineageOS",
        carrier = carrier,
        mcc = mcc,
        mnc = mnc,
        apn = apn,
        types = types,
        protocol = protocol,
        roamingProtocol = roamingProtocol,
        user = "",
        password = "",
        authType = null,
        mmsc = "",
        mmsProxy = "",
        mmsPort = "",
        proxy = "",
        port = "",
        carrierId = carrierId,
        mvnoType = "",
        mvnoMatchData = "",
        carrierEnabled = carrierEnabled,
        userVisible = true,
        userEditable = true,
        networkTypeBitmask = "",
        bearerBitmask = "",
    )
}
