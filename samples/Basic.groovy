@Grab(group="com.pavlovmedia.oss.osgi.http", module="com.pavlovmedia.oss.osgi.http", version="1.1.0-SNAPSHOT")

import com.pavlovmedia.oss.osgi.http.*

new PavlovHttpClientImpl()
                .againstUrl(new URL("http://www.pavlovgo.com"))
                .withVerb(HttpVerbs.GET)
                .execute({println it})
		.ifPresent({println it.getResponseText({println it})})
