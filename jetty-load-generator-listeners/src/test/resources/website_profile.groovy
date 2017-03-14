import org.mortbay.jetty.load.generator.Resource

return new Resource(
        new Resource("index.html",
        new Resource("/style.css",
                new Resource("/logo.gif"),
                new Resource("/spacer.png")
        ),
        new Resource("/fancy.css"),
        new Resource("/script.js",
                new Resource("/library.js"),
                new Resource("/morestuff.js")
        ),
        new Resource("/anotherScript.js"),
        new Resource("/iframeContents.html"),
        new Resource("/moreIframeContents.html"),
        new Resource("/favicon.ico"))
)
