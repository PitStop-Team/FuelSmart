package com.example.findcheapgas.data.remote;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 오피넷 API 키 확인. Repository들은 BuildConfig.OPINET_API_KEY를 넘기고, 여기서는 그 값을 직접 넣어
 * local.properties를 바꾸지 않고 키가 빈 경우를 확인한다.
 */
public class OpinetKeyMissingExceptionTest {

    @Test
    public void emptyOrBlankKey_throwsKeyMissing() {
        assertThrows(OpinetKeyMissingException.class, () -> OpinetKeyMissingException.requireKey(""));
        assertThrows(OpinetKeyMissingException.class, () -> OpinetKeyMissingException.requireKey("   "));
        assertThrows(OpinetKeyMissingException.class, () -> OpinetKeyMissingException.requireKey(null));
    }

    @Test
    public void presentKey_passes() throws OpinetKeyMissingException {
        OpinetKeyMissingException.requireKey("test-key");
    }

    @Test
    public void isOpinetException_soExistingCatchBlocksStillHandleIt() {
        assertTrue(new OpinetKeyMissingException() instanceof OpinetException);
    }
}
