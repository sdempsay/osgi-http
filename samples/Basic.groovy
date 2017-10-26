@Grab(group="com.pavlovmedia.osgi.http", module="com.pavlovmedia.osgi.http",
        version="2.1.0-SNAPSHOT")

import com.pavlovmedia.osgi.http.impl.PavlovHttpClientImpl
import com.pavlovmedia.osgi.http.*

println new PavlovHttpClientImpl()
                .againstUrl(new URL("http://www.pavlovgo.com"))
                .withVerb(HttpVerbs.GET)
                .execute().responseStream.get().get().text