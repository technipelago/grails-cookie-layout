# Cookie Layout

This Grails plugin selects the sitemesh layout to use for groovy server page rendering based on the value of a cookie.

## Configuration

### Cookie name
The name of the cookie can be configured with the `grails.layout.cookie.name` option.

    grails.layout.cookie.name = "grails_layout"

### Append or replace layout name

The value of the layout cookie can either be used as is, or be appended to the layout name specified in the GSP.

    grails.layout.cookie.append = null // cookie value will replace the layout specified in GSP
    grails.layout.cookie.append = '+' // cookie value will be appended to the layout name specified in GSP separated with '+'

    <meta name="layout" content="main">

If layout `main` is specified in the GSP and `grails.layout.cookie.append` is `+` and the cookie value is `foo`,
then Grails will look for a layout named `main+foo.gsp`.

If `grails.layout.cookie.append` is `null` (or false), then Grails will look for a layout named `foo.gsp`.