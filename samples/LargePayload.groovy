@Grab(group="com.pavlovmedia.oss.osgi.http", module="com.pavlovmedia.oss.osgi.http", version="1.1.0-SNAPSHOT")

import com.pavlovmedia.oss.osgi.http.*

new PavlovHttpClientImpl()
                .againstUrl(new URL("https://jsonplaceholder.typicode.com/photos"))
                .withVerb(HttpVerbs.GET)
                .withDebugger({println "DEBUG: "+it})
                .execute({println "ERROR: "+it})
                .ifPresent({println it.getResponseText({println it})})
