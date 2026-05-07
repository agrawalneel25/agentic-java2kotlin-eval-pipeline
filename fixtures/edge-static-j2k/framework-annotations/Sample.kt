package edge.annotations

@Route("csv/import")
class Sample {
    @RequestMapping(method = "POST", consumes = "text/csv")
    fun upload(@Param("payload") payload: String): String {
        return payload.trim { it <= ' ' }
    }
}

internal annotation class Route(val value: String)

internal annotation class RequestMapping(val method: String, val consumes: String)

internal annotation class Param(val value: String)
