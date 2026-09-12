// Passkey registration and login against Spring Security's WebAuthn endpoints.
//
//   POST /webauthn/register/options      -> PublicKeyCredentialCreationOptions (must be logged in)
//   POST /webauthn/register              -> {publicKey: {credential, label}}
//   POST /webauthn/authenticate/options  -> PublicKeyCredentialRequestOptions
//   POST /login/webauthn                 -> the assertion; replies {redirectUrl, authenticated}
//
// All of them are CSRF-protected like the rest of the site. Spring masks the CSRF token per
// request, so the raw XSRF-TOKEN cookie is not accepted in a header; the page renders the masked
// token into a meta tag (layout/base.kte) and that is what gets sent.
import {startAuthentication, startRegistration, WebAuthnAbortService} from '@simplewebauthn/browser'

const csrfToken = () => document.querySelector('meta[name="csrf-token"]')?.content ?? '';

class HttpError extends Error {
    constructor(url, status) {
        super(`${url} failed with HTTP ${status}`);
        this.status = status;
    }
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
        throw new HttpError(url, response.status);
    }
    return response.json();
}

const getJson = async (url) => {
    const response = await fetch(url, {headers: {'Accept': 'application/json'}});
    if (!response.ok) {
        throw new HttpError(url, response.status);
    }
    return response.json();
}

// ---- WebAuthn Signal API: keep the browser's passkey manager in sync with the server ----
// Every call is best-effort: unsupported browsers and failures are silently ignored, since the
// signals only ever remove or relabel stale entries the manager would otherwise keep showing.

const signalSupported = (name) => typeof PublicKeyCredential !== 'undefined' && typeof PublicKeyCredential[name] === 'function';

/**
 * Tells the passkey manager exactly which passkeys the logged-in user still has, per user
 * handle, and the current account name - so passkeys deleted on the profile page disappear from
 * the manager too, and a renamed or merged account shows its current name. Called whenever the
 * profile's passkey list renders.
 */
export const signalAccepted = async () => {
    if (!signalSupported('signalAllAcceptedCredentials')) return;
    try {
        const accepted = await getJson('/passkeys/accepted');
        for (const handle of accepted.handles) {
            await PublicKeyCredential.signalAllAcceptedCredentials({
                rpId: accepted.rpId,
                userId: handle.userId,
                allAcceptedCredentialIds: handle.credentialIds,
            });
            if (signalSupported('signalCurrentUserDetails')) {
                await PublicKeyCredential.signalCurrentUserDetails({
                    rpId: accepted.rpId,
                    userId: handle.userId,
                    name: accepted.name,
                    displayName: accepted.displayName,
                });
            }
        }
    } catch (e) {
        console.log('Passkey signalling skipped:', e);
    }
};

/** After a rejected login: if the server has never heard of the credential, let the manager drop it. */
const signalUnknownIfGone = async (rpId, credentialId) => {
    if (!signalSupported('signalUnknownCredential')) return;
    try {
        const {known} = await getJson(`/passkeys/known?credentialId=${encodeURIComponent(credentialId)}`);
        if (!known) {
            await PublicKeyCredential.signalUnknownCredential({rpId, credentialId});
            console.log('Told the browser to forget a passkey the server no longer knows');
        }
    } catch (e) {
        console.log('Passkey signalling skipped:', e);
    }
};

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
    let optionsJSON;
    let assertion;
    try {
        optionsJSON = await postJson('/webauthn/authenticate/options');
        assertion = await startAuthentication({optionsJSON, useBrowserAutofill: true});
    } catch (e) {
        // Aborted autofill (the user navigated on, or a second call superseded this one) is
        // routine and not worth surfacing.
        console.log('Passkey autofill ended:', e);
        return;
    }
    try {
        const result = await postJson('/login/webauthn', assertion);
        if (result && result.authenticated) {
            redirectAfterLogin();
            return;
        }
        console.log('Passkey login was not accepted', result);
    } catch (e) {
        console.log('Passkey login failed:', e);
        if (e instanceof HttpError && e.status === 401) {
            // The most common reason: the passkey was deleted on the profile page (or the
            // account was removed) but the browser's manager still offers it.
            await signalUnknownIfGone(optionsJSON.rpId, assertion.id);
        }
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

/**
 * Conditional create ("automatic passkey upgrade"): asks the browser to create a passkey without
 * any prompt, which it only does under its own conditions (typically right after a
 * password-manager-assisted sign-in). Resolves true if a passkey was created, false if the
 * browser declined or can't - the caller then decides whether to ask the user explicitly.
 */
export const createSilently = async ({timeoutMs = 4000} = {}) => {
    try {
        if (typeof PublicKeyCredential === 'undefined' || typeof PublicKeyCredential.getClientCapabilities !== 'function') return false;
        const capabilities = await PublicKeyCredential.getClientCapabilities();
        if (!capabilities.conditionalCreate) return false;
        const optionsJSON = await postJson('/webauthn/register/options');
        // Browsers don't always reject promptly when their conditions aren't met - the request
        // can just sit there, and while it does, no other WebAuthn call (including the Signal
        // API) can run. So give it a moment, then cancel the ceremony and move on.
        const timeout = new Promise(resolve => setTimeout(() => resolve(null), timeoutMs));
        const credential = await Promise.race([startRegistration({optionsJSON, useAutoRegister: true}), timeout]);
        if (credential === null) {
            WebAuthnAbortService.cancelCeremony();
            console.log('Automatic passkey creation timed out; asking instead');
            return false;
        }
        await postJson('/webauthn/register', {publicKey: {credential, label: describeThisDevice()}});
        return true;
    } catch (e) {
        console.log('Automatic passkey creation not possible:', e.name ?? e);
        return false;
    }
}

/** Whether this browser can create a passkey on this device at all (drives the post-login prompt). */
export const canOfferPasskey = async () => {
    try {
        return typeof PublicKeyCredential !== 'undefined' && await PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable();
    } catch (e) {
        return false;
    }
}
