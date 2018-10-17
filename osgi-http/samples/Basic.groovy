@Grab(group="com.pavlovmedia.oss.osgi.http", module="com.pavlovmedia.oss.osgi.http", version="1.0.1")

import com.pavlovmedia.oss.osgi.http.*

println new PavlovHttpClientImpl()
                .againstUrl(new URL("http://www.pavlovgo.com"))
                .withVerb(HttpVerbs.GET)
                .execute().getResponseText()