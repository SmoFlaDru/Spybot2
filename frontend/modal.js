import htmx from 'htmx.org'
const modal = new tabler.Modal(document.querySelector("#modal"), {});

htmx.on("htmx:after:swap", (e) => {
    if (e.detail.ctx.target.id === "dialog") {
        modal.show()
    }
})

htmx.on("htmx:before:swap", (e) => {
  // Empty response targeting #dialog => hide the modal
  if (e.detail.ctx.target.id === "dialog" && !e.detail.ctx.text) {
    modal.hide()
    e.preventDefault()
  }
})

htmx.on("hidden.bs.modal", () => {
  document.getElementById("dialog").innerHTML = ""
})
