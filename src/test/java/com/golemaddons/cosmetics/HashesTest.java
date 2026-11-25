package com.golemaddons.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HashesTest {
	@Test
	void uuidRoundTrip() {
		UUID id = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
		assertEquals("069a79f444e94726a5befca90e38aaf5", Hashes.compactUuid(id));
		assertEquals(id, Hashes.parseUuid("069a79f444e94726a5befca90e38aaf5"));
		assertEquals(id, Hashes.parseUuid("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
		assertEquals(id, Hashes.parseUuid("069A79F444E94726A5BEFCA90E38AAF5"));
	}

	@Test
	void malformedUuids() {
		assertNull(Hashes.parseUuid(null));
		assertNull(Hashes.parseUuid("069a79f444e94726a5befca90e38aaf"));
		assertNull(Hashes.parseUuid("069a79f4444e9-4726-a5be-fca90e38aaf5"));
		assertNull(Hashes.parseUuid("zz9a79f444e94726a5befca90e38aaf5"));
		assertNull(Hashes.parseUuid("０".repeat(32)), "fullwidth digits");
	}

	@Test
	void sha256Format() {
		assertTrue(Hashes.isSha256Hex(Hashes.sha256Hex(new byte[] {1, 2, 3})));
		assertFalse(Hashes.isSha256Hex("ABC"));
		assertFalse(Hashes.isSha256Hex("../" + "a".repeat(61)));
		assertFalse(Hashes.isSha256Hex(null));
	}

}
