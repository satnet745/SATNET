package org.servalproject;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.servalproject.relay.RelayPacket;
import org.servalproject.relay.RelayServer;
import org.servalproject.servaldna.SubscriberId;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
public class RequestThreadRelayApiTest {
    private TestServalBatPhoneApplication app;
    private SimpleWebServer webServer;
    private RelayServer relayServer;

    @Before
    public void setUp() {
        app = (TestServalBatPhoneApplication) ApplicationProvider.getApplicationContext();
        tearDownServers();
    }

    @After
    public void tearDown() {
        tearDownServers();
    }

    @Test
    public void relayStatusReturnsOfflineWhenRelayServerMissing() throws Exception {
        webServer = new SimpleWebServer(4410, 4420);
        app.webServer = webServer;
        app.relayServer = null;

        HttpResponse response = sendRequest("GET /api/relay/status HTTP/1.0\r\nHost: localhost\r\n\r\n", webServer.port);

        Assert.assertEquals(503, response.statusCode);
        Assert.assertTrue(response.body.contains("\"offline\""));
    }

    @Test
    public void relayStatusAndSendEndpointsWorkWhenRelayServerRunning() throws Exception {
        relayServer = new RelayServer(4421, 4430);
        webServer = new SimpleWebServer(4431, 4440);
        app.relayServer = relayServer;
        app.webServer = webServer;

        SubscriberId sender = new SubscriberId(repeat('1'));
        SubscriberId recipient = new SubscriberId(repeat('2'));

        Socket recipientSocket = new Socket("127.0.0.1", relayServer.getPort());
        recipientSocket.setSoTimeout(2000);
        BufferedWriter relayWriter = new BufferedWriter(new OutputStreamWriter(recipientSocket.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader relayReader = new BufferedReader(new InputStreamReader(recipientSocket.getInputStream(), StandardCharsets.UTF_8));
        relayWriter.write(RelayPacket.create(RelayPacket.TYPE_REGISTER, recipient, null).encode());
        relayWriter.write('\n');
        relayWriter.flush();

        HttpResponse status = sendRequest("GET /api/relay/status HTTP/1.0\r\nHost: localhost\r\n\r\n", webServer.port);
        Assert.assertEquals(200, status.statusCode);
        Assert.assertTrue(status.body.contains("\"online\""));
        Assert.assertTrue(status.body.contains("\"relayPort\":" + relayServer.getPort()));

        RelayPacket packet = RelayPacket.create(RelayPacket.TYPE_AUDIO, sender, recipient);
        packet.callId = "http123";
        packet.payload = new byte[]{9, 8, 7};
        String encoded = packet.encode();
        HttpResponse send = sendRequest(
                "POST /api/relay/send HTTP/1.0\r\n" +
                        "Host: localhost\r\n" +
                        "Content-Length: " + encoded.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" +
                        encoded,
                webServer.port);
        Assert.assertEquals(202, send.statusCode);
        Assert.assertTrue(send.body.contains("accepted"));

        RelayPacket forwarded = RelayPacket.decode(relayReader.readLine());
        Assert.assertEquals(RelayPacket.TYPE_AUDIO, forwarded.type);
        Assert.assertArrayEquals(packet.payload, forwarded.payload);
        Assert.assertEquals(sender.toHex(), forwarded.from);
        Assert.assertEquals(recipient.toHex(), forwarded.to);

        recipientSocket.close();
    }

    @Test
    public void relaySendRejectsInvalidPayloadAndUnknownPathReturns404() throws Exception {
        relayServer = new RelayServer(4441, 4450);
        webServer = new SimpleWebServer(4451, 4460);
        app.relayServer = relayServer;
        app.webServer = webServer;

        HttpResponse invalid = sendRequest(
                "POST /api/relay/send HTTP/1.0\r\nHost: localhost\r\nContent-Length: 7\r\n\r\ninvalid",
                webServer.port);
        Assert.assertEquals(400, invalid.statusCode);
        Assert.assertTrue(invalid.body.contains("invalid"));

        HttpResponse missing = sendRequest("GET /api/relay/unknown HTTP/1.0\r\nHost: localhost\r\n\r\n", webServer.port);
        Assert.assertEquals(404, missing.statusCode);
        Assert.assertTrue(missing.body.contains("not_found"));
    }

    private HttpResponse sendRequest(String request, int port) throws Exception {
        Socket socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(3000);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer.write(request);
        writer.flush();

        String statusLine = reader.readLine();
        int statusCode = Integer.parseInt(statusLine.split(" ")[1]);
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            // skip headers
        }
        StringBuilder body = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        socket.close();
        return new HttpResponse(statusCode, body.toString());
    }

    private void tearDownServers() {
        if (webServer != null) {
            webServer.interrupt();
            webServer = null;
        }
        if (relayServer != null) {
            relayServer.interrupt();
            relayServer = null;
        }
        if (app != null) {
            app.webServer = null;
            app.relayServer = null;
        }
    }

    private static String repeat(char c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 64; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    private static final class HttpResponse {
        private final int statusCode;
        private final String body;

        private HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }
}

