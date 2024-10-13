export const onChartRenderTrigger = (now, call) => {
    const observer = new MutationObserver(function (mutations) {
        mutations.forEach(function (mutation) {
            if (mutation.type === "attributes" && mutation.attributeName === 'data-bs-theme') {
                call()
            }
        });
    });
    observer.observe(document.body, {
        attributes: true //configure it to listen to attribute changes
    });

    if (now === true) call();
}

export const onChartReady = (call) => {
    document.addEventListener("DOMContentLoaded", call)
};

export const isDarkMode = () => {
    return document.body.classList.contains("theme-dark");
}

