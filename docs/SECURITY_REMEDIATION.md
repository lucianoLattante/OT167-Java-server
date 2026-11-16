# Authentication hardening (proposed)

This change addresses a subset of this educational application's risks. It is not a production-readiness certification.

## Configuration and migration

JWT_SECRET is mandatory: Base64 encoding of at least 32 cryptographically random bytes, used as decoded bytes for HS256. Generate it locally with a cryptographic random generator; never commit or log it. Length checks cannot establish entropy. Startup fails when configuration is missing, malformed or too short.

Configure a fresh key on every instance when integrating this patch and require users to sign in again. No old-key fallback is provided. Token lifetime remains ten hours; issuer/audience and refresh/revocation policies remain future work.

DB_USERNAME and DB_PASSWORD replace database literals. Email is disabled by default; SendGrid and AWS credentials come from environment variables. See [.env.example](../.env.example) for names only. Spring Boot does not load .env automatically. Full application startup and external integrations have not been verified.

## Behavior

- Tokens require HS256, a nonblank subject and expiration.
- Invalid bearer tokens receive HTTP 401 without exception details.
- Disabled, locked or expired accounts are rejected.
- DELETE /users/{id} requires ADMIN.
- Anonymous protected requests retain the existing form-login redirect.

## Verification

Java 11.0.17, Maven 3.9.0, Windows; JUnit Jupiter via Surefire 2.22.2.

```shell
mvn -B -Dtest=JwtUtilsSecurityTest,UserDeletionSecurityTest,NewsServiceTest,CommentAuthorizationTest,CommentAuthorizationWebTest test
```

Local verification used offline mode and an existing Maven cache: 36 tests passed, zero failures/errors/skips. Eight JWT unit tests, seven user-deletion MockMvc tests, ten comment service tests, four comment MockMvc tests and seven existing news service tests. Persistence and external providers are mocked; this is not database integration testing. No live email, cloud or payment credentials are used.

The unused TestNG RELEASE dependency was removed after checking no TestNG imports exist; duplicate JUnit/Mockito declarations were removed so Surefire selects JUnit. This does not upgrade the legacy dependency stack.

## Still open

Credential revocation and historical exposure require separate owner action. Other authorization policies and remaining legacy dependency vulnerabilities, deployment, database integration and the full legacy test suite remain outside this patch. Removing literals does not revoke credentials or clean Git history.

## Interview discussion

Why is signature verification insufficient without claim validation? Why do authentication and role authorization need separate tests? What does MockMvc leave unverified? How does key rotation affect existing sessions?

## Comment ownership and logging follow-up

Comment creation binds the author to the authenticated account. Owner or ADMIN may update/delete; update changes only the body and preserves author/news/identity. Rejected calls return explicit 403 and cannot silently succeed. The controller preserves not-found errors and uses a localization fallback after successful deletion.

The direct Log4j 1.2.17 dependency was removed and UtilsLog migrated to the existing SLF4J facade; FATAL maps to ERROR. The initial compile exposed this legacy adapter, which was migrated before rerunning verification. An HTTP test then exposed a catch-all delete controller that returned 403 after successful mutation when localization was unavailable; that catch was removed. Final selected run: 36 tests, zero failures/errors/skips. This is not a full dependency or database-integration clearance for the legacy project.
