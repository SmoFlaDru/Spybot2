// Passkey registration and login against Spring Security's WebAuthn endpoints.
//
//   POST /webauthn/register/options      -> PublicKeyCredentialCreationOptions (must be logged in)
//   POST /webauthn/register              -> {publicKey: {credential, label}}
//   POST /webauthn/authenticate/options  -> PublicKeyCredentialRequestOptions
//   POST /login/webauthn                 -> the assertion; replies {redirectUrl, authenticated}
//
// All of them are CSRF-protected like the rest of the site, so every request carries the token
// from the XSRF-TOKEN cookie.
import {startAuthentication, startRegistration} from '@simplewebauthn/browser'

const csrfToken = () => {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : '';
}

const postJson = async (url, body) => {
    const response = await fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': csrfToken(),
        },
        body: JSON.stringify(body ?? {}),
    });
    if (!response.ok) {
        throw new Error(`${url} failed with HTTP ${response.status}`);
    }
    return response.json();
}

const isAllowedRedirectUrl = url => /^[A-Za-z0-9/]+$/.test(url);

const redirectAfterLogin = () => {
    const nextUrl = new URLSearchParams(window.location.search).get('next');
    window.location.href = nextUrl !== null && isAllowedRedirectUrl(nextUrl) ? nextUrl : '/profile';
}

// A label for the new passkey as it will appear on the profile page. The provider (iCloud
// Keychain, Windows Hello, ...) is derived server-side from the authenticator's AAGUID.
const describeThisDevice = () => {
    const ua = navigator.userAgent;
    const device =
        /iPhone/.test(ua) ? 'iPhone' :
        /iPad/.test(ua) ? 'iPad' :
        /Android/.test(ua) ? 'Android device' :
        /Macintosh/.test(ua) ? 'Mac' :
        /Windows/.test(ua) ? 'Windows PC' :
        /Linux/.test(ua) ? 'Linux device' : 'This device';
    const browser =
        /Edg\//.test(ua) ? 'Edge' :
        /Firefox\//.test(ua) ? 'Firefox' :
        /Chrome\//.test(ua) ? 'Chrome' :
        /Safari\//.test(ua) ? 'Safari' : 'browser';
    return `${device} (${browser})`;
}

/**
 * Conditional UI on the login page: offers the user's passkeys in the browser's autofill for the
 * username field and completes the login when one is picked.
 */
export const autocomplete = async () => {
    try {
        const optionsJSON = await postJson('/webauthn/authenticate/options');
        const assertion = await startAuthentication({optionsJSON, useBrowserAutofill: true});
        const result = await postJson('/login/webauthn', assertion);
        if (result && result.authenticated) {
            redirectAfterLogin();
        } else {
            console.log('Passkey login was not accepted', result);
        }
    } catch (e) {
        // Aborted autofill (the user navigated on, or a second call superseded this one) is
        // routine and not worth surfacing.
        console.log('Passkey autofill ended:', e);
    }
};

/** Registers a new passkey for the logged-in user. Resolves on success, throws otherwise. */
export const create = async () => {
    const optionsJSON = await postJson('/webauthn/register/options');

    let credential;
    try {
        credential = await startRegistration({optionsJSON});
    } catch (error) {
        if (error.name === 'InvalidStateError') {
            throw new Error('This authenticator is already registered for your account');
        }
        throw error;
    }

    await postJson('/webauthn/register', {publicKey: {credential, label: describeThisDevice()}});
    return 'Success!';
}
