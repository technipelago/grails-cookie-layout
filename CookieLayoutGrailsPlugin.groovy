/*
 * Copyright (c) 2013 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import grails.plugin.cookielayout.CookiePageLayoutFinder
import grails.util.Environment
import org.codehaus.groovy.grails.web.context.GrailsConfigUtils
import org.codehaus.groovy.grails.web.pages.GroovyPagesTemplateEngine

class CookieLayoutGrailsPlugin {
    def version = "0.7"
    def grailsVersion = "2.0 > *"
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]
    def loadAfter = ['groovyPages']
    def title = "Select layout based on cookie"
    def author = "Goran Ehrsson"
    def authorEmail = "goran@technipelago.se"
    def description = '''\
Sitemesh layout can be selected by looking at the current domain name or the value of a cookie.
This can be used to support different themes for different users running the same application.
'''
    def documentation = "https://github.com/technipelago/grails-cookie-layout"
    def license = "APACHE"
    def organization = [name: "Technipelago AB", url: "http://www.technipelago.se/"]
    def issueManagement = [system: "github", url: "https://github.com/technipelago/grails-cookie-layout/issues"]
    def scm = [url: "https://github.com/technipelago/grails-cookie-layout"]

    def doWithSpring = {
        def config = application.flatConfig
        boolean developmentMode = !application.warDeployed
        Environment env = Environment.current
        boolean enableReload = env.isReloadEnabled() ||
                GrailsConfigUtils.isConfigTrue(application, GroovyPagesTemplateEngine.CONFIG_PROPERTY_GSP_ENABLE_RELOAD) ||
                (developmentMode && env == Environment.DEVELOPMENT)

        groovyPageLayoutFinder(CookiePageLayoutFinder) {
            gspReloadEnabled = enableReload
            defaultDecoratorName = config['grails.sitemesh.default.layout'] ?: null
            enableNonGspViews = config['grails.sitemesh.enable.nongsp'] ?: false
            viewResolver = ref('jspViewResolver')
            cookieLayoutName = config['grails.layout.cookie.name'] ?: "grails_layout"
            cookieLayoutAppend = config['grails.layout.cookie.append'] ?: null
            checkRequest = config['grails.layout.cookie.request'] ?: false
        }
    }
}
