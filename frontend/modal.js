
const modal = new bootstrap.Modal(document.querySelector("#modal"), {});

htmx.on("htmx:afterSwap", (e) => {
    if (e.detail.target.id === "dialog") {
        modal.show()
    }
})

htmx.on("htmx:beforeSwap", (e) => {
  // Empty response targeting #dialog => hide the modal
  if (e.detail.target.id === "dialog" && !e.detail.xhr.response) {
    modal.hide()
    e.detail.shouldSwap = false
  }
})

htmx.on("hidden.bs.modal", () => {
  document.getElementById("dialog").innerHTML = ""
})
