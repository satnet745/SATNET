package org.servalproject.relay;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import org.servalproject.servaldna.SubscriberId;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
public class RelayServerTest {
	private RelayServer server;

	@After
	public void tearDown() {
		if (server != null) {
			server.interrupt();
			server = null;
		}
	}

	@Test
	public void relayServerForwardsSessionAndAudioPackets() throws Exception {
		server = new RelayServer(4210, 4220);

		SubscriberId peerA = new SubscriberId(repeat('a'));
		SubscriberId peerB = new SubscriberId(repeat('b'));

		Socket socketA = new Socket("127.0.0.1", server.getPort());
		Socket socketB = new Socket("127.0.0.1", server.getPort());
		socketA.setSoTimeout(2000);
		socketB.setSoTimeout(2000);

		BufferedWriter writerA = new BufferedWriter(new OutputStreamWriter(socketA.getOutputStream(), StandardCharsets.UTF_8));
		BufferedReader readerA = new BufferedReader(new InputStreamReader(socketA.getInputStream(), StandardCharsets.UTF_8));
		BufferedWriter writerB = new BufferedWriter(new OutputStreamWriter(socketB.getOutputStream(), StandardCharsets.UTF_8));
		BufferedReader readerB = new BufferedReader(new InputStreamReader(socketB.getInputStream(), StandardCharsets.UTF_8));

		writePacket(writerA, RelayPacket.create(RelayPacket.TYPE_REGISTER, peerA, null));
		writePacket(writerB, RelayPacket.create(RelayPacket.TYPE_REGISTER, peerB, null));

		RelayPacket session = RelayPacket.create(RelayPacket.TYPE_SESSION, peerA, peerB);
		session.callId = "call123";
		writePacket(writerA, session);

		RelayPacket sessionAck = RelayPacket.decode(readerA.readLine());
		Assert.assertEquals(RelayPacket.TYPE_SESSION_ACK, sessionAck.type);
		Assert.assertEquals(peerB.toHex(), sessionAck.from);
		Assert.assertEquals(peerA.toHex(), sessionAck.to);
		Assert.assertEquals("call123", sessionAck.callId);

		RelayPacket forwardedSession = RelayPacket.decode(readerB.readLine());
		Assert.assertEquals(RelayPacket.TYPE_SESSION, forwardedSession.type);
		Assert.assertEquals(peerA.toHex(), forwardedSession.from);
		Assert.assertEquals(peerB.toHex(), forwardedSession.to);

		RelayPacket audio = RelayPacket.create(RelayPacket.TYPE_AUDIO, peerA, peerB);
		audio.callId = "call123";
		audio.codec = 1;
		audio.sampleStart = 320;
		audio.sequence = 7;
		audio.payload = new byte[]{1, 2, 3, 4};
		writePacket(writerA, audio);

		RelayPacket forwardedAudio = RelayPacket.decode(readerB.readLine());
		Assert.assertEquals(RelayPacket.TYPE_AUDIO, forwardedAudio.type);
		Assert.assertArrayEquals(audio.payload, forwardedAudio.payload);
		Assert.assertEquals(320, forwardedAudio.sampleStart);
		Assert.assertEquals(7, forwardedAudio.sequence);

		socketA.close();
		socketB.close();
	}

	private static void writePacket(BufferedWriter writer, RelayPacket packet) throws Exception {
		writer.write(packet.encode());
		writer.write('\n');
		writer.flush();
	}

	private static String repeat(char c) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 64; i++) {
			sb.append(c);
		}
		return sb.toString();
	}
}

