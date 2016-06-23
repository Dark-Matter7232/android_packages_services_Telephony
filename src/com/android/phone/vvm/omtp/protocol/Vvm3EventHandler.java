/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.phone.vvm.omtp.protocol;

import android.annotation.IntDef;
import android.content.Context;
import android.telecom.PhoneAccountHandle;
import android.util.Log;

import com.android.phone.VoicemailStatus;
import com.android.phone.settings.VoicemailChangePinDialogPreference;
import com.android.phone.vvm.omtp.DefaultOmtpEventHandler;
import com.android.phone.vvm.omtp.OmtpEvents;
import com.android.phone.vvm.omtp.OmtpEvents.Type;
import com.android.phone.vvm.omtp.utils.PhoneAccountHandleConverter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Handles {@link OmtpEvents} when {@link Vvm3Protocol} is being used. This handler writes custom
 * error codes into the voicemail status table so support on the dialer side is required.
 *
 * TODO(b/29577838) disable VVM3 by default so support on system dialer can be ensured.
 */
public class Vvm3EventHandler {

    private static final String TAG = "Vvm3EventHandler";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({VMS_DNS_FAILURE, VMG_DNS_FAILURE, SPG_DNS_FAILURE, VMS_NO_CELLULAR, VMG_NO_CELLULAR,
            SPG_NO_CELLULAR, VMS_TIMEOUT, VMG_TIMEOUT, STATUS_SMS_TIMEOUT, SUBSCRIBER_BLOCKED,
            UNKNOWN_USER, UNKNOWN_DEVICE, INVALID_PASSWORD, MAILBOX_NOT_INITIALIZED,
            SERVICE_NOT_PROVISIONED, SERVICE_NOT_ACTIVATED, USER_BLOCKED, IMAP_GETQUOTA_ERROR,
            IMAP_SELECT_ERROR, IMAP_ERROR, VMG_INTERNAL_ERROR, VMG_DB_ERROR,
            VMG_COMMUNICATION_ERROR, SPG_URL_NOT_FOUND, VMG_UNKNOWN_ERROR, PIN_NOT_SET})
    public @interface ErrorCode {

    }

    public static final int VMS_DNS_FAILURE = -9001;
    public static final int VMG_DNS_FAILURE = -9002;
    public static final int SPG_DNS_FAILURE = -9003;
    public static final int VMS_NO_CELLULAR = -9004;
    public static final int VMG_NO_CELLULAR = -9005;
    public static final int SPG_NO_CELLULAR = -9006;
    public static final int VMS_TIMEOUT = -9007;
    public static final int VMG_TIMEOUT = -9008;
    public static final int STATUS_SMS_TIMEOUT = -9009;

    public static final int SUBSCRIBER_BLOCKED = -9990;
    public static final int UNKNOWN_USER = -9991;
    public static final int UNKNOWN_DEVICE = -9992;
    public static final int INVALID_PASSWORD = -9993;
    public static final int MAILBOX_NOT_INITIALIZED = -9994;
    public static final int SERVICE_NOT_PROVISIONED = -9995;
    public static final int SERVICE_NOT_ACTIVATED = -9996;
    public static final int USER_BLOCKED = -9998;
    public static final int IMAP_GETQUOTA_ERROR = -9997;
    public static final int IMAP_SELECT_ERROR = -9989;
    public static final int IMAP_ERROR = -9999;

    public static final int VMG_INTERNAL_ERROR = -101;
    public static final int VMG_DB_ERROR = -102;
    public static final int VMG_COMMUNICATION_ERROR = -103;
    public static final int SPG_URL_NOT_FOUND = -301;

    // Non VVM3 codes:
    public static final int VMG_UNKNOWN_ERROR = -1;
    public static final int PIN_NOT_SET = -100;


    public static void handleEvent(Context context, int subId, OmtpEvents event) {
        boolean handled = false;
        switch (event.getType()) {
            case Type.CONFIGURATION:
                handled = handleConfigurationEvent(context, subId, event);
                break;
            case Type.DATA_CHANNEL:
                handled = handleDataChannelEvent(context, subId, event);
                break;
            case Type.NOTIFICATION_CHANNEL:
                handled = handleNotificationChannelEvent(context, subId, event);
                break;
            case Type.OTHER:
                handled = handleOtherEvent(context, subId, event);
                break;
            default:
                com.android.services.telephony.Log
                        .wtf(TAG, "invalid event type " + event.getType() + " for " + event);
        }
        if (!handled) {
            DefaultOmtpEventHandler.handleEvent(context, subId, event);
        }
    }

    private static boolean handleConfigurationEvent(Context context, int subId,
            OmtpEvents event) {
        switch (event) {
            case CONFIG_REQUEST_STATUS_SUCCESS:
                PhoneAccountHandle handle = PhoneAccountHandleConverter.fromSubId(subId);
                if (VoicemailChangePinDialogPreference.getDefaultOldPin(context, handle) == null) {
                    return false;
                } else {
                    postError(context, subId, PIN_NOT_SET);
                }
                break;
            case CONFIG_DEFAULT_PIN_REPLACED:
                postError(context, subId, PIN_NOT_SET);
                break;
            default:
                return false;
        }
        return true;
    }

    private static boolean handleDataChannelEvent(Context context, int subId,
            OmtpEvents event) {
        switch (event) {
            case DATA_NO_CONNECTION:
            case DATA_NO_CONNECTION_CELLULAR_REQUIRED:
                postError(context, subId, VMS_NO_CELLULAR);
                break;
            case DATA_CANNOT_RESOLVE_HOST_ON_NETWORK:
                postError(context, subId, VMS_DNS_FAILURE);
                break;
            case DATA_BAD_IMAP_CREDENTIAL:
                postError(context, subId, IMAP_ERROR);
                break;
            case DATA_AUTH_UNKNOWN_USER:
                postError(context, subId, UNKNOWN_USER);
                break;
            case DATA_AUTH_UNKNOWN_DEVICE:
                postError(context, subId, UNKNOWN_DEVICE);
                break;
            case DATA_AUTH_INVALID_PASSWORD:
                postError(context, subId, INVALID_PASSWORD);
                break;
            case DATA_AUTH_MAILBOX_NOT_INITIALIZED:
                postError(context, subId, MAILBOX_NOT_INITIALIZED);
                break;
            case DATA_AUTH_SERVICE_NOT_PROVISIONED:
                postError(context, subId, SERVICE_NOT_PROVISIONED);
                break;
            case DATA_AUTH_SERVICE_NOT_ACTIVATED:
                postError(context, subId, SERVICE_NOT_ACTIVATED);
                break;
            case DATA_AUTH_USER_IS_BLOCKED:
                postError(context, subId, USER_BLOCKED);
                break;

            case DATA_INVALID_PORT:
            case DATA_SSL_INVALID_HOST_NAME:
            case DATA_CANNOT_ESTABLISH_SSL_SESSION:
            case DATA_IOE_ON_OPEN:
            case DATA_REJECTED_SERVER_RESPONSE:
            case DATA_INVALID_INITIAL_SERVER_RESPONSE:
            case DATA_SSL_EXCEPTION:
            case DATA_ALL_SOCKET_CONNECTION_FAILED:
                postError(context, subId, IMAP_ERROR);
                break;

            default:
                return false;
        }
        return true;
    }

    private static boolean handleNotificationChannelEvent(Context context, int subId,
            OmtpEvents event) {
        return false;
    }

    private static boolean handleOtherEvent(Context context, int subId, OmtpEvents event) {
        switch (event) {
            case VVM3_NEW_USER_SETUP_FAILED:
                postError(context, subId, MAILBOX_NOT_INITIALIZED);
                break;
            case VVM3_VMG_DNS_FAILURE:
                postError(context, subId, VMG_DNS_FAILURE);
                break;
            case VVM3_SPG_DNS_FAILURE:
                postError(context, subId, SPG_DNS_FAILURE);
                break;
            case VVM3_VMG_CONNECTION_FAILED:
                postError(context, subId, VMG_NO_CELLULAR);
                break;
            case VVM3_SPG_CONNECTION_FAILED:
                postError(context, subId, SPG_NO_CELLULAR);
                break;
            case VVM3_VMG_TIMEOUT:
                postError(context, subId, VMG_TIMEOUT);
                break;

            case VVM3_SUBSCRIBER_PROVISIONED:
                postError(context, subId, SERVICE_NOT_ACTIVATED);
            case VVM3_SUBSCRIBER_BLOCKED:
                postError(context, subId, SUBSCRIBER_BLOCKED);
            default:
                return false;
        }
        return true;
    }

    private static void postError(Context context, int subId, @ErrorCode int errorCode) {
        VoicemailStatus.Editor editor = VoicemailStatus.edit(context, subId);

        switch (errorCode) {
            case VMG_DNS_FAILURE:
            case SPG_DNS_FAILURE:
            case VMG_NO_CELLULAR:
            case SPG_NO_CELLULAR:
            case VMG_TIMEOUT:
            case SUBSCRIBER_BLOCKED:
            case UNKNOWN_USER:
            case UNKNOWN_DEVICE:
            case INVALID_PASSWORD:
            case MAILBOX_NOT_INITIALIZED:
            case SERVICE_NOT_PROVISIONED:
            case SERVICE_NOT_ACTIVATED:
            case USER_BLOCKED:
            case VMG_UNKNOWN_ERROR:
            case SPG_URL_NOT_FOUND:
            case VMG_INTERNAL_ERROR:
            case VMG_DB_ERROR:
            case VMG_COMMUNICATION_ERROR:
            case PIN_NOT_SET:
                editor.setConfigurationState(errorCode);
                break;
            case VMS_NO_CELLULAR:
            case VMS_DNS_FAILURE:
            case VMS_TIMEOUT:
            case IMAP_GETQUOTA_ERROR:
            case IMAP_SELECT_ERROR:
            case IMAP_ERROR:
                editor.setDataChannelState(errorCode);
                break;
            case STATUS_SMS_TIMEOUT:
                editor.setNotificationChannelState(errorCode);
                break;
            default:
                Log.wtf(TAG, "unknown error code: " + errorCode);
        }
        editor.apply();
    }
}