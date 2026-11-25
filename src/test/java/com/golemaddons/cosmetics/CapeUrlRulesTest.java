package com.golemaddons.cosmetics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CapeUrlRulesTest {
	private static final String OWN = "api.example.workers.dev";

	@ParameterizedTest
	@ValueSource(strings = {"https://i.imgur.com/abc.png", "https://example.com:443/cape.png",
			"https://cdn.example.co.uk/a/b.png?x=1", "https://xn--bcher-kva.example/cape.png"})
	void accepts(String url) {
		assertTrue(CapeUrlRules.check(url, OWN).valid(), url);
	}

	@ParameterizedTest
	@CsvSource({
			"http://example.com/c.png, NOT_HTTPS",
			"ftp://example.com/c.png, NOT_HTTPS",
			"file:///etc/passwd, NOT_HTTPS",
			"javascript:alert(1), NOT_HTTPS",
			"https://example.com:8443/c.png, BAD_PORT",
			"https://user:pw@example.com/c.png, HAS_CREDENTIALS",
			"https://user@example.com/c.png, HAS_CREDENTIALS",
			"https://127.0.0.1/c.png, IP_ADDRESS",
			"https://10.0.0.5/c.png, IP_ADDRESS",
			"https://2130706433/c.png, IP_ADDRESS",
			"https://0x7f.1/c.png, IP_ADDRESS",
			"https://0177.0.0.1/c.png, IP_ADDRESS",
			"https://[::1]/c.png, IP_ADDRESS",
			"https://[fd00::1]/c.png, IP_ADDRESS",
			"https://localhost/c.png, PRIVATE_HOST",
			"https://foo.localhost/c.png, PRIVATE_HOST",
			"https://printer.local/c.png, PRIVATE_HOST",
			"https://metadata.internal/c.png, PRIVATE_HOST",
			"https://router.home.arpa/c.png, PRIVATE_HOST",
			"https://intranet/c.png, PRIVATE_HOST",
			"https://api.example.workers.dev/v1/cape/x.png, PRIVATE_HOST",
			"https://exa mple.com/c.png, MALFORMED",
	})
	void rejects(String url, CapeUrlRules.Problem expected) {
		assertEquals(expected, CapeUrlRules.check(url, OWN).problem(), url);
	}

	@Test
	void rejectsRawUnicodeAndEmpty() {
		assertEquals(CapeUrlRules.Problem.MALFORMED, CapeUrlRules.check("https://exämple.com/c.png", OWN).problem());
		assertEquals(CapeUrlRules.Problem.EMPTY, CapeUrlRules.check("  ", OWN).problem());
		assertEquals(CapeUrlRules.Problem.EMPTY, CapeUrlRules.check(null, OWN).problem());
	}

	@Test
	void tooLong() {
		assertEquals(CapeUrlRules.Problem.TOO_LONG, CapeUrlRules.check("https://example.com/" + "a".repeat(600), null).problem());
	}
}
