package com.gcplot;

import com.gcplot.commons.ErrorMessages;
import com.gcplot.commons.serialization.JsonSerializer;
import com.gcplot.controllers.gc.EventsController;
import com.gcplot.messages.*;
import com.gcplot.model.VMVersion;
import com.gcplot.model.gc.GarbageCollectorType;
import com.google.common.collect.Sets;
import io.vertx.core.json.JsonObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:art.dm.ser@gmail.com">Artem Dmitriev</a>
 *         8/23/16
 */
public class GCTests extends IntegrationTest {
    private static final long DAYS_BACK = TimeUnit.DAYS.toMillis(30);

    @Test
    public void testAnalyseController() throws Exception {
        String token = login();
        get("/analyse/all", token, j -> r(j).getJsonArray("analyses").size() == 0);
        String analyseId = createAnalyse(token);
        Assert.assertNotNull(analyseId);

        get("/analyse/all", token, j -> r(j).getJsonArray("analyses").size() == 1);
        JsonObject analyseJson = get("/analyse/get?id=" + analyseId, token);
        Assert.assertNotNull(analyseJson);
        AnalyseResponse ar = JsonSerializer.deserialize(r(analyseJson).toString(), AnalyseResponse.class);
        Assert.assertEquals(analyseId, ar.id);
        Assert.assertEquals("analyse1", ar.name);
        Assert.assertEquals(false, ar.continuous);
        Assert.assertEquals("Africa/Harare", ar.timezone);
        Assert.assertTrue(ar.jvmIds.isEmpty());
        Assert.assertTrue(ar.jvmHeaders.isEmpty());
        Assert.assertTrue(ar.jvmVersions.isEmpty());
        Assert.assertTrue(ar.jvmGCTypes.isEmpty());
        Assert.assertTrue(ar.memory.isEmpty());

        post("/analyse/update", new UpdateAnalyseRequest(analyseId, "anan", "Europe/Moscow", ""), token, success());

        get("/analyse/get?id=123", token, ErrorMessages.INTERNAL_ERROR);
        get("/analyse/get?id=" + UUID.randomUUID().toString(), token, ErrorMessages.RESOURCE_NOT_FOUND_RESPONSE);

        post("/analyse/jvm/add", new AddJvmRequest("jvm1", analyseId, "JVM 1", VMVersion.HOTSPOT_1_9.type(),
                GarbageCollectorType.ORACLE_SERIAL.type(), "h1,h2", null), token, success());
        post("/analyse/jvm/add", new AddJvmRequest("jvm2", analyseId, "JVM 2", VMVersion.HOTSPOT_1_3_1.type(),
                GarbageCollectorType.ORACLE_PAR_OLD_GC.type(), null, new MemoryStatus(1,2,3,4,5)),
                token, success());

        ar = getAnalyse(token, analyseId);
        Assert.assertEquals(analyseId, ar.id);
        Assert.assertEquals("anan", ar.name);
        Assert.assertEquals("Europe/Moscow", ar.timezone);
        Assert.assertEquals(Sets.newHashSet("jvm1", "jvm2"), ar.jvmIds);
        Assert.assertEquals("JVM 1", ar.jvmNames.get("jvm1"));
        Assert.assertEquals("JVM 2", ar.jvmNames.get("jvm2"));
        Assert.assertEquals(VMVersion.HOTSPOT_1_9.type(), (int) ar.jvmVersions.get("jvm1"));
        Assert.assertEquals(VMVersion.HOTSPOT_1_3_1.type(), (int) ar.jvmVersions.get("jvm2"));
        Assert.assertEquals(GarbageCollectorType.ORACLE_SERIAL.type(), (int) ar.jvmGCTypes.get("jvm1"));
        Assert.assertEquals(GarbageCollectorType.ORACLE_PAR_OLD_GC.type(), (int) ar.jvmGCTypes.get("jvm2"));
        Assert.assertEquals("h1,h2", ar.jvmHeaders.get("jvm1"));
        Assert.assertNull(ar.jvmHeaders.get("jvm2"));
        Assert.assertEquals(new MemoryStatus(1,2,3,4,5), ar.memory.get("jvm2"));
        Assert.assertNull(ar.memory.get("jvm1"));

        post("/analyse/jvm/update/info", new UpdateJvmInfoRequest(analyseId, "jvm2", null,
                new MemoryStatus(11,12,13,14,15)), token, success());
        ar = getAnalyse(token, analyseId);
        Assert.assertNull(ar.jvmHeaders.get("jvm2"));
        Assert.assertEquals(new MemoryStatus(11,12,13,14,15), ar.memory.get("jvm2"));
        Assert.assertNull(ar.memory.get("jvm1"));

        post("/analyse/jvm/update/version", new UpdateJvmVersionRequest(analyseId, "jvm1", "JVM N", null,
                GarbageCollectorType.ORACLE_G1.type()), token, success());
        ar = getAnalyse(token, analyseId);
        Assert.assertEquals(GarbageCollectorType.ORACLE_G1.type(), (int) ar.jvmGCTypes.get("jvm1"));
        Assert.assertEquals(GarbageCollectorType.ORACLE_PAR_OLD_GC.type(), (int) ar.jvmGCTypes.get("jvm2"));
        Assert.assertEquals("JVM N", ar.jvmNames.get("jvm1"));
        Assert.assertEquals("JVM 2", ar.jvmNames.get("jvm2"));

        post("/analyse/jvm/update/version", new UpdateJvmVersionRequest(analyseId, "jvm1", null, VMVersion.HOTSPOT_1_2_2.type(),
                null), token, success());
        ar = getAnalyse(token, analyseId);
        Assert.assertEquals(VMVersion.HOTSPOT_1_2_2.type(), (int) ar.jvmVersions.get("jvm1"));
        Assert.assertEquals(VMVersion.HOTSPOT_1_3_1.type(), (int) ar.jvmVersions.get("jvm2"));
        Assert.assertEquals("JVM N", ar.jvmNames.get("jvm1"));
        Assert.assertEquals("JVM 2", ar.jvmNames.get("jvm2"));

        post("/analyse/jvm/update/version", new UpdateJvmVersionRequest(analyseId, "jvm1", null, null, null), token, ErrorMessages.INTERNAL_ERROR);

        Assert.assertEquals(1, (int) r(delete("/analyse/jvm/delete?analyse_id=" + analyseId + "&jvm_id=jvm1", token)).getInteger("success"));
        ar = getAnalyse(token, analyseId);
        Assert.assertEquals(analyseId, ar.id);
        Assert.assertEquals(Sets.newHashSet("jvm2"), ar.jvmIds);
        Assert.assertEquals(new MemoryStatus(11,12,13,14,15), ar.memory.get("jvm2"));
        Assert.assertNull(ar.jvmNames.get("jvm1"));
        Assert.assertEquals("JVM 2", ar.jvmNames.get("jvm2"));

        delete("/analyse/delete?id=" + UUID.randomUUID().toString(), token);
        get("/analyse/all", token, j -> r(j).getJsonArray("analyses").size() == 1);
        Assert.assertEquals(1, (int) r(delete("/analyse/delete?id=" + analyseId, token)).getInteger("success"));
        get("/analyse/all", token, j -> r(j).getJsonArray("analyses").size() == 0);
    }

    @Test
    public void simpleLogsUploadTest() throws Exception {
        String token = login();
        String analyseId = createAnalyse(token);
        String jvmId = "jvm1";
        post("/analyse/jvm/add", new AddJvmRequest(jvmId, analyseId, "JVM 1", VMVersion.HOTSPOT_1_8.type(),
                GarbageCollectorType.ORACLE_CMS.type(), null, null), token, success());
        JsonObject resp = processGCLogFile(token, analyseId, jvmId, "hs18_log_cms.log");
        Assert.assertTrue(success().test(resp));

        AnalyseResponse ar = getAnalyse(token, analyseId);
        Assert.assertTrue(ar.lastEventUTC.get(jvmId) > 0);

        List<GCEventResponse> events = getEvents(token, analyseId, jvmId, ar.lastEventUTC.get(jvmId) - TimeUnit.DAYS.toMillis(30),
                ar.lastEventUTC.get(jvmId));
        Assert.assertEquals(19, events.size());

        // check that processing this file again won't make any effect
        resp = processGCLogFile(token, analyseId, jvmId, "hs18_log_cms.log");
        Assert.assertTrue(success().test(resp));
        AnalyseResponse ar2 = getAnalyse(token, analyseId);
        Assert.assertEquals(ar.lastEventUTC, ar2.lastEventUTC);

        events = getEvents(token, analyseId, jvmId, ar.lastEventUTC.get(jvmId) - DAYS_BACK, ar.lastEventUTC.get(jvmId));
        Assert.assertEquals(19, events.size());

        // test chunked response
        List<GCEventResponse> streamEvents =
                getEventsStream(token, analyseId, jvmId, ar.lastEventUTC.get(jvmId) - DAYS_BACK, ar.lastEventUTC.get(jvmId));
        Assert.assertEquals(19, streamEvents.size());

        get("/gc/jvm/events/erase/all" + "?" + "analyse_id=" + analyseId + "&jvm_id=" + jvmId, token, success());
        events = getEvents(token, analyseId, jvmId, ar.lastEventUTC.get(jvmId) - DAYS_BACK, ar.lastEventUTC.get(jvmId));
        Assert.assertEquals(0, events.size());
        streamEvents =
                getEventsStream(token, analyseId, jvmId, ar.lastEventUTC.get(jvmId) - DAYS_BACK, ar.lastEventUTC.get(jvmId));
        Assert.assertEquals(0, streamEvents.size());

        JsonObject oaj = get("/jvm/gc/ages/last" + "?" + "analyse_id=" + analyseId + "&jvm_id=" + jvmId, token);
        ObjectsAgesResponse oa = JsonSerializer.deserialize(r(oaj).toString(), ObjectsAgesResponse.class);
        Assert.assertEquals(5, oa.occupied.size());
        Assert.assertEquals(5, oa.total.size());
        Assert.assertTrue(oa.time > 0);

        get("/jvm/gc/ages/erase" + "?" + "analyse_id=" + analyseId + "&jvm_id=" + jvmId, token, success());
        oaj = get("/jvm/gc/ages/last" + "?" + "analyse_id=" + analyseId + "&jvm_id=" + jvmId, token);
        Assert.assertTrue(oaj.isEmpty());

        // test case with empty analyse and jvm

        resp = processGCLogFile(token, "", "", "hs18_log_cms.log");
        Assert.assertTrue(success().test(resp));
        ar = getAnalyse(token, EventsController.ANONYMOUS_ANALYSE_ID);

        events = getEvents(token, EventsController.ANONYMOUS_ANALYSE_ID, ar.jvmIds.iterator().next(),
                ar.lastEventUTC.get(ar.jvmIds.iterator().next()) - TimeUnit.DAYS.toMillis(30),
                ar.lastEventUTC.get(ar.jvmIds.iterator().next()));
        Assert.assertEquals(19, events.size());

        resp = processGCLogFile(token, "", "", "hs18_log_cms.log");
        Assert.assertTrue(success().test(resp));
        ar2 = getAnalyse(token, EventsController.ANONYMOUS_ANALYSE_ID);
        Assert.assertEquals(ar2.lastEventUTC.size(), 2);
        Assert.assertEquals(ar.lastEventUTC.values().iterator().next(), ar2.lastEventUTC.values().iterator().next());

        Assert.assertEquals(ar2.jvmIds.size(), 2);
        for (String jvm : ar2.jvmIds) {
            events = getEvents(token, EventsController.ANONYMOUS_ANALYSE_ID, jvm, ar2.lastEventUTC.get(jvm) - DAYS_BACK, ar2.lastEventUTC.get(jvm));
            Assert.assertEquals(19, events.size());
        }
    }

    private JsonObject processGCLogFile(String token, String analyseId, String jvmId, String fileName) throws IOException {
        HttpClient hc = HttpClientBuilder.create().build();
        HttpEntity file = MultipartEntityBuilder.create()
                .addBinaryBody("gc.log", GCTests.class.getClassLoader().getResourceAsStream(fileName),
                        ContentType.TEXT_PLAIN, fileName).build();
        HttpPost post = new HttpPost("http://" + LOCALHOST + ":" +
                getPort() + "/gc/jvm/log/process?token=" + token + "&analyse_id=" + analyseId
                + "&jvm_id=" + jvmId + "&sync=true");
        post.setEntity(file);
        HttpResponse response = hc.execute(post);
        Assert.assertEquals(200, response.getStatusLine().getStatusCode());
        return new JsonObject(EntityUtils.toString(response.getEntity()));
    }

    private String createAnalyse(String token) throws Exception {
        NewAnalyseRequest nar = new NewAnalyseRequest("analyse1", false, "Africa/Harare", Collections.emptyList(), "");
        return r(post("/analyse/new", nar, token, j -> r(j).getString("id") != null)).getString("id");
    }

    private AnalyseResponse getAnalyse(String token, String analyseId) throws Exception {
        JsonObject analyseJson;
        analyseJson = get("/analyse/get?id=" + analyseId, token);
        return JsonSerializer.deserialize(r(analyseJson).toString(), AnalyseResponse.class);
    }

    private List<GCEventResponse> getEventsStream(String token, String analyseId, String jvmId,
                                            long from, long to) throws Exception {
        List<JsonObject> ejs;
        ejs = getChunked("/gc/jvm/events/stream?analyse_id=" + analyseId + "&jvm_id=" + jvmId + "&from=" + from + "&to=" + to,
                token);
        return ejs.stream().filter(e -> !e.isEmpty()).map(j -> JsonSerializer.deserialize(j.toString(), GCEventResponse.class)).collect(Collectors.toList());
    }

    private List<GCEventResponse> getEvents(String token, String analyseId, String jvmId,
                                            long from, long to) throws Exception {
        JsonObject eventsJson;
        eventsJson = get("/gc/jvm/events?analyse_id=" + analyseId + "&jvm_id=" + jvmId + "&from=" + from + "&to=" + to,
                token);
        return JsonSerializer.deserializeList(ra(eventsJson).toString(), GCEventResponse.class);
    }

    private String login() throws Exception {
        RegisterRequest request = new RegisterRequest("admin", null, null, "root", "a@b.c");
        post("/user/register", request, success());

        JsonObject jo = login(request);
        return jo.getString("token");
    }

}
