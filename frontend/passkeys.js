import {startAuthentication, startRegistration} from '@simplewebauthn/browser'

const isAllowedRedirectUrl = url => {
    const regex = /^[A-Za-z0-9/]+$/;
    return regex.test(str);
}

const sendToServerForVerificationAndLogin = async (response) => {
    try {
        console.log("sendToServerForVerificationAndLogin:", response);
        const verificationResp = await fetch('/passkeys/verify-authentication', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(response),
        });
        const verificationJSON = await verificationResp.json();

        // Show UI appropriate for the `verified` status
        if (verificationJSON && verificationJSON.verified) {
            console.log("success")
            const urlParams = new URLSearchParams(window.location.search);
            let nextUrl = urlParams.get('next');
            if (nextUrl === null || !isAllowedRedirectUrl(nextUrl)) {
                nextUrl = '/profile';
            }
            window.location.href = nextUrl;
        } else {
            console.log("error", verificationJSON);
        }
    } catch (e) {
        handleError(e);
    }
}

const handleError = (error) => {
    console.log("An error occurred:", error);
}

export const autocomplete = async () => {
    try {
        console.log("Setting up autocomplete");
        const options = await fetch('/passkeys/generate-authentication-options')
        const optionsPayload = (await options.json())["publicKey"]
        // delete options["allowedCredentials"]
        const response = await startAuthentication(optionsPayload, true)
        await sendToServerForVerificationAndLogin(response)
    } catch (e) {
        handleError(e);
    }
};

export const create = async () => {
    const resp = await fetch('/passkeys/generate-registration-options');

    let attResp;
    try {
        // Pass the options to the authenticator and wait for a response
        attResp = await startRegistration((await resp.json()).publicKey);
    } catch (error) {
        // Some basic error handling
        if (error.name === 'InvalidStateError') {
            throw Error('Error: Authenticator was probably already registered by user');
        } else {
            throw Error(error);
        }
    }

    // POST the response to the endpoint that calls
    // @simplewebauthn/server -> verifyRegistrationResponse()
    const verificationResp = await fetch('/passkeys/verify-registration', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(attResp),
    });

    // Wait for the results of verification
    const verificationJSON = await verificationResp.json();

    // Show UI appropriate for the `verified` status
    if (verificationJSON && verificationJSON.verified) {
        return 'Success!';
    } else {
        throw Error(`Oh no, something went wrong! Response: <pre>${JSON.stringify(verificationJSON)}</pre>`);
    }
}
