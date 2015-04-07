# Cookie Layout

This Grails plugin selects the sitemesh layout to use for groovy server page rendering based on the value of a cookie.

Normally you specify the layout to use with the `layout` meta tag.

    <meta name="layout" content="main">

This plugin checks if a **layout cookie** is set and uses it's value to lookup a different layout than the one specified in the GSP.
This feature can be used to let users select themes from a pre-defined list of theme names.
You just have to create a **settings** page and let the user select a custom theme. Then set the layout cookie to the selected theme.
This plugin will handle the rest, i.e. select the user preferred theme when rendering GSP pages.

## Configuration

### Cookie name
The name of the cookie can be configured with the `grails.layout.cookie.name` option.

    grails.layout.cookie.name = "grails_layout"

### Append or replace layout name

The value of the layout cookie can either be used as is, or be appended to the layout name specified in the GSP.

    grails.layout.cookie.append = null // cookie value will replace the layout specified in GSP
    grails.layout.cookie.append = '+' // cookie value will be appended to the layout name specified in GSP separated with '+'

#### Examples:

If layout `main` is specified in the GSP and `grails.layout.cookie.append` is `+` and the cookie value is `foo`,
then Grails will look for a layout named `main+foo.gsp`.

    grails.layout.cookie.append = '+'

If layout `main` is specified in the GSP and `grails.layout.cookie.append` is `null` or `false` and the cookie value is `foo`,
then Grails will look for a layout named `foo.gsp`.

    grails.layout.cookie.append = null

### Changes

* Version 0.7 supports Grails 2.4.x
* Version 0.6 supports Grails 2.2.x