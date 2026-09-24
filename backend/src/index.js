/*
 * Better PV backend - a small Cloudflare Worker that holds the Hypixel API key so the mod doesn't have to.
 *
 * Flow:
 *   1. Every logged-in Minecraft client has a profile key pair (used for chat signing) whose public key Mojang
 *      has signed together with the account UUID. The mod signs "betterpv-auth:<uuid>:<timestamp>" with the
 *      private key and POSTs /auth with the signature, the public key and Mojang's signature of it.
 *   2. We check Mojang's signature with Mojang's published keys (built in below - no call to Mojang, whose
 *      servers block Cloudflare), then the player's signature. If both hold, we hand back a signed token (HMAC,
 *      no storage needed).
 *   3. The mod calls GET /hypixel/<endpoint>?<args> with "Authorization: Bearer <token>". We check the token,
 *      only allow the endpoints/arguments the profile viewer uses, rate-limit per Minecraft account, cache
 *      responses briefly, and forward to api.hypixel.net with our key.
 *
 * Secrets (set in the Cloudflare dashboard or with `wrangler secret put`):
 *   HYPIXEL_API_KEY - the Hypixel API key
 *   TOKEN_SECRET    - any long random string, used to sign auth tokens
 */

const TOKEN_TTL_SECONDS = 60 * 60;

// Per-account request budget. Counted per Worker isolate (Cloudflare runs several), so this is a soft limit
// that stops a single misbehaving client, not an exact global count. The response cache below is what keeps
// total Hypixel usage low.
const RATE_LIMIT_WINDOW_MS = 60 * 1000;
const RATE_LIMIT_MAX_REQUESTS = 30;

// Endpoint -> [required query argument, cache seconds]. Anything not listed here is rejected.
const ALLOWED_ENDPOINTS = {
	"player": ["uuid", 300],
	"v2/player": ["uuid", 300],
	"status": ["uuid", 60],
	"v2/status": ["uuid", 60],
	"guild": ["player", 600],
	"v2/guild": ["player", 600],
	"skyblock/bingo": ["uuid", 300],
	"v2/skyblock/bingo": ["uuid", 300],
	"v2/skyblock/profiles": ["uuid", 180],
};

const UUID_PATTERN = /^[0-9a-f]{32}$/;

// How far a login request's timestamp may be from the Worker's clock.
const AUTH_CLOCK_SKEW_MS = 5 * 60 * 1000;

// Mojang's player-certificate public keys, from https://api.minecraftservices.com/publickeys
// ("playerCertificateKeys"). Built in because Mojang's servers refuse requests from Cloudflare Workers.
// If Mojang ever rotates these, logins will fail with "not signed by Mojang" - refresh them from that URL.
const MOJANG_PLAYER_CERTIFICATE_KEYS = [
	"MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAylB4B6m5lz7jwrcFz6Fd/fnfUhcvlxsTSn5kIK/2aGG1C3kMy4VjhwlxF6BFUSnfxhNswPjh3ZitkBxEAFY25uzkJFRwHwVA9mdwjashXILtR6OqdLXXFVyUPIURLOSWqGNBtb08EN5fMnG8iFLgEJIBMxs9BvF3s3/FhuHyPKiVTZmXY0WY4ZyYqvoKR+XjaTRPPvBsDa4WI2u1zxXMeHlodT3lnCzVvyOYBLXL6CJgByuOxccJ8hnXfF9yY4F0aeL080Jz/3+EBNG8RO4ByhtBf4Ny8NQ6stWsjfeUIvH7bU/4zCYcYOq4WrInXHqS8qruDmIl7P5XXGcabuzQstPf/h2CRAUpP/PlHXcMlvewjmGU6MfDK+lifScNYwjPxRo4nKTGFZf/0aqHCh/EAsQyLKrOIYRE0lDG3bzBh8ogIMLAugsAfBb6M3mqCqKaTMAf/VAjh5FFJnjS+7bE+bZEV0qwax1CEoPPJL1fIQjOS8zj086gjpGRCtSy9+bTPTfTR/SJ+VUB5G2IeCItkNHpJX2ygojFZ9n5Fnj7R9ZnOM+L8nyIjPu3aePvtcrXlyLhH/hvOfIOjPxOlqW+O5QwSFP4OEcyLAUgDdUgyW36Z5mB285uKW/ighzZsOTevVUG2QwDItObIV6i8RCxFbN2oDHyPaO5j1tTaBNyVt8CAwEAAQ==",
	"MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAt4t9NPuu7cktclnaH7eZj0omkLcJHeLz5MKsyJEntHZ0INtuBjSSul3Pp3pBeJN8k3ADdcdBLUN90bcAi7WsQqTx3Ft363q3W7TbM8j2iTEdp/0uVspoRt/DP1tkaWFs/w2WwUv9jbVoBUzfUc4pSTIxRwdjmqjZQfvjwKNDbOx3IhP2H0WXodbISejPi1wBZqNW4m1rnZAXp/EpUguxA8mobCa4vUCBkyFDyXdl69/wUSJHyCPmgcMJ364OlAhIqtwVPShBZObvrK/f0BYk6ShJD3N7TFDatSYsIIdcTKRknaIm91s+EsMrdB9U4Yw+ZJ/pyCB4S3vk8zfDCnb0DWIxYH3/EMzaxl77djmTmMzi/JDITup5z3jfWtRZmrAhU2/+W5IO5hEpo3/bCS9PXIY5xb41Lmp2ZO8dXKtyD66Chchy0W129n8vPl2GIruOdrxsjZAHnneyAb9jm0uaGaphwnEnuecX/qgHY6ZMtayvLLsPst8PO6R1vufMy8WqjK+j7LnC1krL7CPDg0NEhyQTmw5l+NCNjSlvB1juM9V4PARg0bYCOkGXm7ydRCjSSH8CJXZpwnd5cBB5WKAX3KPzutRgMi/LFwNSMZzFuUyXaYOZPpD259yqph1LmGqegEdDriACVU+dVEONFMm8eIuBofe7ljmsAFKW9BINwK0CAwEAAQ==",
];

// In-memory response cache, per isolate. (Cloudflare's Cache API does nothing on *.workers.dev addresses, so
// this is what actually saves Hypixel requests when several people look up the same player.) SkyBlock
// profile responses can be a few hundred KB, so the entry count is kept modest to stay well under the
// isolate's memory limit.
const RESPONSE_CACHE_MAX_ENTRIES = 150;
const responseCache = new Map();

const rateLimitBuckets = new Map();

export default {
	async fetch(request, env) {
		const url = new URL(request.url);
		try {
			if (url.pathname === "/auth" && request.method === "POST") {
				return await handleAuth(request, env);
			}
			if (request.method !== "GET") {
				return json({success: false, cause: "Method not allowed"}, 405);
			}
			if (url.pathname === "/") {
				return json({success: true, service: "Better PV backend"});
			}
			if (url.pathname.startsWith("/hypixel/")) {
				return await handleHypixel(request, url, env);
			}
			return json({success: false, cause: "Not found"}, 404);
		} catch (e) {
			console.error(e);
			return json({success: false, cause: "Internal error"}, 500);
		}
	},
};

async function handleAuth(request, env) {
	let body;
	try {
		body = await request.json();
	} catch (e) {
		return json({success: false, cause: "Bad request body"}, 400);
	}
	const {uuid, keyExpiresAt, publicKey, keySignature, timestamp, signature} = body || {};
	if (typeof uuid !== "string" || !UUID_PATTERN.test(uuid) ||
		!Number.isSafeInteger(keyExpiresAt) || !Number.isSafeInteger(timestamp) ||
		typeof publicKey !== "string" || typeof keySignature !== "string" || typeof signature !== "string") {
		return json({success: false, cause: "Bad request body"}, 400);
	}

	const now = Date.now();
	if (Math.abs(now - timestamp) > AUTH_CLOCK_SKEW_MS) {
		return json({success: false, cause: "Login request expired - check your computer's clock"}, 401);
	}
	if (keyExpiresAt < now) {
		return json({success: false, cause: "Minecraft profile key has expired"}, 401);
	}

	let publicKeyBytes, keySignatureBytes, signatureBytes;
	try {
		publicKeyBytes = base64Decode(publicKey);
		keySignatureBytes = base64Decode(keySignature);
		signatureBytes = base64Decode(signature);
	} catch (e) {
		return json({success: false, cause: "Bad request body"}, 400);
	}

	// 1. Mojang vouches that this public key belongs to this account (same check a vanilla server does).
	if (!await verifyMojangKeySignature(uuid, keyExpiresAt, publicKeyBytes, keySignatureBytes)) {
		return json({success: false, cause: "Minecraft profile key is not signed by Mojang"}, 401);
	}

	// 2. The caller holds the matching private key.
	let playerKey;
	try {
		playerKey = await crypto.subtle.importKey(
			"spki", publicKeyBytes, {name: "RSASSA-PKCS1-v1_5", hash: "SHA-256"}, false, ["verify"]
		);
	} catch (e) {
		return json({success: false, cause: "Bad profile public key"}, 400);
	}
	const message = new TextEncoder().encode("betterpv-auth:" + uuid + ":" + timestamp);
	if (!await crypto.subtle.verify("RSASSA-PKCS1-v1_5", playerKey, signatureBytes, message)) {
		return json({success: false, cause: "Login signature is invalid"}, 401);
	}

	const expiresAt = Math.floor(now / 1000) + TOKEN_TTL_SECONDS;
	const token = await signToken({sub: uuid, exp: expiresAt}, env.TOKEN_SECRET);
	return json({success: true, token, expiresAt});
}

// Mojang signs [uuid most-significant 8 bytes][uuid least-significant 8 bytes][key expiry millis, 8 bytes]
// [X.509 public key] with SHA1withRSA (net.minecraft.world.entity.player.ProfilePublicKey.Data#signedPayload).
async function verifyMojangKeySignature(uuid, keyExpiresAt, publicKeyBytes, keySignatureBytes) {
	const payload = new Uint8Array(24 + publicKeyBytes.length);
	for (let i = 0; i < 16; i++) {
		payload[i] = parseInt(uuid.substr(i * 2, 2), 16);
	}
	new DataView(payload.buffer).setBigInt64(16, BigInt(keyExpiresAt), false);
	payload.set(publicKeyBytes, 24);

	for (const mojangKey of await mojangKeys()) {
		if (await crypto.subtle.verify("RSASSA-PKCS1-v1_5", mojangKey, keySignatureBytes, payload)) {
			return true;
		}
	}
	return false;
}

let importedMojangKeys = null;

async function mojangKeys() {
	if (!importedMojangKeys) {
		importedMojangKeys = await Promise.all(MOJANG_PLAYER_CERTIFICATE_KEYS.map(key => crypto.subtle.importKey(
			"spki", base64Decode(key), {name: "RSASSA-PKCS1-v1_5", hash: "SHA-1"}, false, ["verify"]
		)));
	}
	return importedMojangKeys;
}

async function handleHypixel(request, url, env) {
	const auth = request.headers.get("Authorization") || "";
	const claims = auth.startsWith("Bearer ") ? await verifyToken(auth.slice(7), env.TOKEN_SECRET) : null;
	if (!claims) {
		return json({success: false, cause: "Invalid or expired token"}, 401);
	}

	const endpoint = url.pathname.slice("/hypixel/".length);
	const rule = ALLOWED_ENDPOINTS[endpoint];
	if (!rule) {
		return json({success: false, cause: "Endpoint not allowed"}, 403);
	}
	const [argName, cacheSeconds] = rule;
	const argValue = (url.searchParams.get(argName) || "").replace(/-/g, "").toLowerCase();
	if (!UUID_PATTERN.test(argValue)) {
		return json({success: false, cause: "Missing or invalid " + argName}, 400);
	}

	// Normalised upstream URL - also the cache key, so it never includes the caller's token.
	const upstreamUrl = "https://api.hypixel.net/" + endpoint + "?" + argName + "=" + argValue;
	const now = Date.now();
	const cached = responseCache.get(upstreamUrl);
	if (cached && cached.expires > now) {
		return new Response(cached.body, {status: 200, headers: {"Content-Type": "application/json"}});
	}

	if (!takeRateLimit(claims.sub)) {
		return json({success: false, cause: "Too many requests, slow down"}, 429);
	}

	const upstream = await fetch(upstreamUrl, {
		headers: {"API-Key": env.HYPIXEL_API_KEY, "User-Agent": "BetterPV-Backend"},
	});
	const body = await upstream.text();
	if (upstream.status === 200) {
		putCache(upstreamUrl, body, now + cacheSeconds * 1000, now);
	}
	return new Response(body, {status: upstream.status, headers: {"Content-Type": "application/json"}});
}

function putCache(key, body, expires, now) {
	if (responseCache.size >= RESPONSE_CACHE_MAX_ENTRIES) {
		for (const [k, v] of responseCache) {
			if (v.expires <= now) responseCache.delete(k);
		}
		// Still full of live entries: evict the oldest insertions (Map iterates in insertion order).
		for (const k of responseCache.keys()) {
			if (responseCache.size < RESPONSE_CACHE_MAX_ENTRIES) break;
			responseCache.delete(k);
		}
	}
	responseCache.delete(key);
	responseCache.set(key, {body, expires});
}

function takeRateLimit(subject) {
	const now = Date.now();
	let bucket = rateLimitBuckets.get(subject);
	if (!bucket || now - bucket.start >= RATE_LIMIT_WINDOW_MS) {
		bucket = {start: now, count: 0};
		rateLimitBuckets.set(subject, bucket);
		if (rateLimitBuckets.size > 10000) {
			// Drop expired buckets so a long-lived isolate doesn't grow without bound.
			for (const [key, value] of rateLimitBuckets) {
				if (now - value.start >= RATE_LIMIT_WINDOW_MS) rateLimitBuckets.delete(key);
			}
		}
	}
	bucket.count++;
	return bucket.count <= RATE_LIMIT_MAX_REQUESTS;
}

// Tokens are "<base64url(json claims)>.<base64url(HMAC-SHA256 of the first part)>".
async function signToken(claims, secret) {
	const payload = base64UrlEncode(new TextEncoder().encode(JSON.stringify(claims)));
	const signature = await hmac(payload, secret);
	return payload + "." + base64UrlEncode(signature);
}

async function verifyToken(token, secret) {
	const dot = token.indexOf(".");
	if (dot <= 0) return null;
	const payload = token.slice(0, dot);
	let signature;
	try {
		signature = base64UrlDecode(token.slice(dot + 1));
	} catch (e) {
		return null;
	}
	const key = await hmacKey(secret);
	const valid = await crypto.subtle.verify("HMAC", key, signature, new TextEncoder().encode(payload));
	if (!valid) return null;
	let claims;
	try {
		claims = JSON.parse(new TextDecoder().decode(base64UrlDecode(payload)));
	} catch (e) {
		return null;
	}
	if (typeof claims.sub !== "string" || typeof claims.exp !== "number") return null;
	if (claims.exp < Math.floor(Date.now() / 1000)) return null;
	return claims;
}

async function hmacKey(secret) {
	if (!secret) throw new Error("TOKEN_SECRET is not configured");
	return crypto.subtle.importKey(
		"raw", new TextEncoder().encode(secret), {name: "HMAC", hash: "SHA-256"}, false, ["sign", "verify"]
	);
}

async function hmac(message, secret) {
	const key = await hmacKey(secret);
	return new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message)));
}

function base64UrlEncode(bytes) {
	let binary = "";
	for (const b of bytes) binary += String.fromCharCode(b);
	return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64UrlDecode(text) {
	return base64Decode(text.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((text.length + 3) % 4));
}

function base64Decode(text) {
	const binary = atob(text);
	const bytes = new Uint8Array(binary.length);
	for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
	return bytes;
}

function json(obj, status = 200) {
	return new Response(JSON.stringify(obj), {
		status,
		headers: {"Content-Type": "application/json"},
	});
}
