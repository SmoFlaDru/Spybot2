// Directional slide for cross-document view transitions (see styles.css).
// Compares the nav position of the page we came from and the page we are
// revealing, and tags the transition so CSS can slide left or right.
// Browsers without cross-document view transition support never fire pagereveal.
window.addEventListener("pagereveal", (event) => {
    if (!event.viewTransition || !window.navigation || !navigation.activation) return;

    const fromUrl = navigation.activation.from && navigation.activation.from.url;
    if (!fromUrl) return;

    const paths = Array.from(document.querySelectorAll("#navbar-menu .navbar-nav .nav-link"))
        .map((link) => new URL(link.href, location.href).pathname);
    const fromIndex = paths.indexOf(new URL(fromUrl).pathname);
    const toIndex = paths.indexOf(location.pathname);
    if (fromIndex < 0 || toIndex < 0 || fromIndex === toIndex) return;

    event.viewTransition.types.add(toIndex > fromIndex ? "slide-forward" : "slide-back");
});
