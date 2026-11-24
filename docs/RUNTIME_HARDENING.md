# Runtime security and review

Normal startup initializes standard roles without creating demo accounts. A disposable database can opt into the `demo` Spring profile and provide `ONG_DEMO_ADMIN_PASSWORD`. That profile creates a synthetic admin only if there are no users. Previously seeded accounts remain in their original database and require a separate owner decision before a rollout. Team credits remain in the README and original commit history.

The user listing requires administrator privileges. A regular account can read only its own user record. News comments use a filtered query with a stable page of ten rows. Use zero-based `?page=` to continue.

Uploads accept decoded PNG and JPEG data up to 5 MiB and 4096 by 4096 pixels. The storage key and temporary path are generated independently of the supplied filename. The upload path attempts cleanup on success and failure. Validate your storage configuration without uploading real files during verification.

Verify the targeted security regressions with `mvn -B -Dtest=CommentAuthorizationTest,CommentAuthorizationWebTest,UserReadAuthorizationWebTest,DemoStartupAndUploadTest test` using Java 11. These tests use synthetic accounts and a mocked storage client.
