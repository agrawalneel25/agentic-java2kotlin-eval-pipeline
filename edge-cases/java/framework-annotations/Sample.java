package edge.annotations;

@Route("csv/import")
public final class Sample {
    @RequestMapping(method = "POST", consumes = "text/csv")
    public String upload(@Param("payload") String payload) {
        return payload.trim();
    }
}

@interface Route {
    String value();
}

@interface RequestMapping {
    String method();
    String consumes();
}

@interface Param {
    String value();
}
