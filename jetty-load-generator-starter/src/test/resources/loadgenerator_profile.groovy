import org.mortbay.jetty.load.generator.profile.Resource
import org.mortbay.jetty.load.generator.profile.ResourceProfile

return new ResourceProfile(new Resource( "index.html",
                                         new Resource( "/css/bootstrap.css",
                                                       new Resource( "/css/bootstrap-theme.css" ),
                                                       new Resource( "/js/jquery-3.1.1.min.js"),
                                                       new Resource( "/js/jquery-3.1.1.min.js"),
                                                       new Resource( "/js/jquery-3.1.1.min.js"),
                                                       new Resource( "/js/jquery-3.1.1.min.js")
                                         ),
                                         new Resource( "/js/bootstrap.js" ,
                                                       new Resource( "/js/bootstrap.js" ),
                                                       new Resource( "/js/bootstrap.js" ),
                                                       new Resource( "/js/bootstrap.js" )
                                         ),
                                         new Resource( "/hello" ),
                                         new Resource( "/dump.jsp?wine=foo&foo=bar" ),
                                         new Resource( "/not_here.html" ),
                                         new Resource( "/hello?name=foo" ),
                                         new Resource( "/hello?name=foo" ),
                                         new Resource( "/upload" ).method("PUT").size(8192),
                                         )
);