## ADDED Requirements

### Requirement: Anonymous clients can create applications through a public REST resource
The system SHALL expose cookie-free link-application CAPTCHA and creation operations under the
public `api.link.halo.run/v1alpha1` API group while visitor submission is enabled.

#### Scenario: Anonymous client requests a REST CAPTCHA
- **WHEN** an anonymous client sends `POST` to
  `/apis/api.link.halo.run/v1alpha1/link-applications/captcha`
- **AND** visitor submission is enabled
- **THEN** the request is admitted without authentication, a CSRF token, or a cookie

#### Scenario: Anonymous client submits a REST application
- **WHEN** an anonymous client sends an otherwise valid JSON request to
  `/apis/api.link.halo.run/v1alpha1/link-applications`
- **AND** visitor submission is enabled
- **THEN** the request is admitted without authentication, a CSRF token, or a cookie

#### Scenario: Anonymous access is minimally authorized
- **WHEN** the plugin registers anonymous REST permissions
- **THEN** it grants only the public create operations needed for the application and CAPTCHA
  resources
- **AND** does not grant list, get, update, delete, approval, rejection, verification, cleanup, or
  other management permissions

#### Scenario: Visitor submission is disabled
- **WHEN** either the application master switch or visitor-submission child switch is disabled
- **THEN** the REST CAPTCHA operation returns `404 Not Found`
- **AND** the REST application operation returns `403 Forbidden`
- **AND** neither operation creates challenge or application state

#### Scenario: Browser sends a cross-origin request
- **WHEN** a browser origin is allowed by Halo's effective `/apis/**` CORS configuration
- **THEN** the REST operations can be called without credentials
- **AND** the plugin does not install a separate CORS policy or origin allowlist

### Requirement: REST CAPTCHA challenges use an explicit JSON contract
The system SHALL issue the existing first-party visitor CAPTCHA to REST clients through an explicit,
short-lived challenge identifier instead of a cookie.

#### Scenario: REST CAPTCHA is issued
- **WHEN** an admitted REST CAPTCHA request succeeds
- **THEN** the system returns `200 OK` with `application/json`
- **AND** the body contains an opaque `challengeId`
- **AND** the body contains the generated PNG as `image` using a
  `data:image/png;base64,...` value
- **AND** the body contains `expiresInSeconds=300`
- **AND** the response sets `Cache-Control: no-store`
- **AND** the response does not set a challenge cookie

#### Scenario: Multiple REST challenges are requested
- **WHEN** one REST client successfully requests another CAPTCHA
- **THEN** the system creates another independent challenge
- **AND** does not invalidate a previously issued REST challenge solely because a newer one was
  issued

#### Scenario: REST and native Form generation share admission
- **WHEN** CAPTCHA requests arrive through both visitor transports from the same remote IP
- **THEN** they share the existing generation rate limit
- **AND** their outstanding challenges share the same bounded process-local store and rendering
  concurrency

#### Scenario: REST CAPTCHA generation is rate limited
- **WHEN** a REST CAPTCHA request exceeds the shared generation rate limit
- **THEN** Halo returns a `429 Too Many Requests` Problem Detail
- **AND** its `type` is `https://halo.run/probs/request-not-permitted`
- **AND** it contains the positive `retryAfterSeconds`

#### Scenario: REST CAPTCHA generation is unavailable
- **WHEN** challenge capacity, rendering capacity, encoding, or storage is unavailable
- **THEN** Halo returns a `503 Service Unavailable` Problem Detail
- **AND** its `type` is `https://halo.run/probs/link-application-unavailable`
- **AND** no unusable challenge is returned

### Requirement: REST application requests use a stable JSON schema
The system SHALL accept one JSON request schema for general link-application clients and SHALL map
it to the shared visitor submission model.

#### Scenario: Client submits all supported fields
- **WHEN** a client sends `application/json` containing `url`, `displayName`, `logo`,
  `description`, `email`, `backlink`, `feedUrls`, `challengeId`, and `captchaCode`
- **THEN** `feedUrls` is interpreted as an array of strings
- **AND** the remaining application fields retain their existing meanings

#### Scenario: Client omits a required field
- **WHEN** `url`, `displayName`, `challengeId`, or `captchaCode` is missing, null, or blank
- **THEN** Halo returns a `400 Bad Request` Problem Detail
- **AND** no LinkApplication is created

#### Scenario: Optional values are normalized
- **WHEN** an otherwise valid request contains optional strings or `feedUrls`
- **THEN** every string is trimmed
- **AND** omitted, null, or blank optional strings become null
- **AND** omitted, null, or empty `feedUrls` becomes an empty list
- **AND** blank `feedUrls` elements are discarded after trimming

#### Scenario: Request contains an unknown property
- **WHEN** an otherwise valid JSON request contains a property outside the published schema
- **THEN** the decoder ignores that property
- **AND** processes the known properties normally

#### Scenario: Request media type is not JSON
- **WHEN** a client submits the REST application operation with a request media type other than
  `application/json`
- **THEN** Halo returns `415 Unsupported Media Type`
- **AND** the system does not verify a challenge or create a LinkApplication

#### Scenario: JSON is malformed
- **WHEN** a client submits malformed JSON
- **THEN** Halo returns `400 Bad Request`
- **AND** the system does not verify a challenge or create a LinkApplication

### Requirement: REST submissions reuse the complete visitor application workflow
The system SHALL adapt a decoded REST request into one `FORM`-origin submission and SHALL reuse the
same settings, CAPTCHA, per-IP submission limiter, validation, duplicate, capacity, persistence,
and notification behavior as the native Form transport.

#### Scenario: REST application is created
- **WHEN** visitor submission is enabled
- **AND** a REST client submits valid application fields with a valid explicit CAPTCHA
- **AND** the request passes the shared submission limiter, duplicate checks, and capacity
- **THEN** the shared creation service persists one `PENDING` LinkApplication
- **AND** its origin type is `FORM`

#### Scenario: CAPTCHA is invalid
- **WHEN** `challengeId` or `captchaCode` is missing, malformed, incorrect, expired, or replayed
- **THEN** the system atomically consumes any supplied matching challenge before comparison
- **AND** does not call the shared LinkApplication creation service
- **AND** does not consume the formal application submission allowance

#### Scenario: Decoded submission reaches a later failure
- **WHEN** a decoded REST submission passes CAPTCHA verification
- **AND** later fails rate admission, field validation, duplicate detection, capacity, or operational
  creation
- **THEN** its CAPTCHA remains consumed
- **AND** a retry requires a newly issued challenge

#### Scenario: REST and native Form submissions share rate admission
- **WHEN** CAPTCHA-validated requests from the same remote IP arrive through both transports within
  one minute
- **THEN** the shared limiter atomically admits at most one request
- **AND** changing transports does not bypass the allowance

#### Scenario: Existing duplicate rules apply
- **WHEN** a REST submission matches a formal Link or a blocking LinkApplication under the existing
  canonical and source-aware rules
- **THEN** the shared creation service returns its existing duplicate outcome
- **AND** treating the REST submission as `FORM` preserves the existing rejected-form and
  rejected-Comment behavior

#### Scenario: Existing validation applies
- **WHEN** REST application fields reach shared validation
- **THEN** the system applies the same required-field and HTTP-or-HTTPS URL rules as native Form
  submission
- **AND** the REST transport does not introduce separate email, text-length, or other field rules

#### Scenario: Notification publication fails after persistence
- **WHEN** the shared creation service persists the LinkApplication
- **AND** notification publication fails
- **THEN** the REST operation still reports successful creation

### Requirement: REST creation returns a minimal application reference
The system SHALL return the created application's stable metadata name and initial status without
exposing submitted values or an anonymous read operation.

#### Scenario: REST creation succeeds
- **WHEN** the shared creation service creates a `PENDING` LinkApplication
- **THEN** the REST operation returns `201 Created`
- **AND** the JSON body contains its metadata name as `id`
- **AND** contains `status=PENDING`

#### Scenario: Created response protects application data
- **WHEN** the REST operation returns `201 Created`
- **THEN** it does not return submitted fields or a LinkApplication resource representation
- **AND** it does not provide a `Location` for an anonymous application read operation

### Requirement: REST failures use Halo Problem Details
The system SHALL delegate expected REST failures to Halo's global
`application/problem+json` exception handling and SHALL expose stable Problem type URIs for
machine decisions.

#### Scenario: Halo renders an expected REST failure
- **WHEN** the REST endpoint raises an expected status exception
- **THEN** the response contains `type`, `title`, `status`, `detail`, `instance`, `requestId`, and
  `timestamp` according to Halo's Problem Details handling
- **AND** clients can branch on `status` and `type`
- **AND** clients are not required to parse `detail`

#### Scenario: Application fields are invalid
- **WHEN** shared field validation rejects a REST submission
- **THEN** Halo returns `400 Bad Request`
- **AND** `type` is `https://halo.run/probs/invalid-link-application`
- **AND** the Problem Detail contains Halo-style `errors` as an array of display strings

#### Scenario: CAPTCHA is invalid
- **WHEN** REST CAPTCHA verification rejects the supplied challenge or answer
- **THEN** Halo returns `400 Bad Request`
- **AND** `type` is `https://halo.run/probs/invalid-link-application-captcha`

#### Scenario: Visitor submission is disabled
- **WHEN** the REST application operation is unavailable because visitor submission is disabled
- **THEN** Halo returns `403 Forbidden`
- **AND** `type` is `https://halo.run/probs/link-application-disabled`

#### Scenario: Application is duplicated
- **WHEN** shared duplicate detection rejects the submission
- **THEN** Halo returns `409 Conflict`
- **AND** `type` is `https://halo.run/probs/duplicate-link-application`

#### Scenario: Pending capacity is reached
- **WHEN** shared creation rejects the submission because pending capacity is exhausted
- **THEN** Halo returns `409 Conflict`
- **AND** `type` is `https://halo.run/probs/link-application-capacity-reached`
- **AND** the configured capacity is not exposed

#### Scenario: Submission is rate limited
- **WHEN** the REST application operation exceeds the shared submission rate limit
- **THEN** Halo returns `429 Too Many Requests`
- **AND** `type` is `https://halo.run/probs/request-not-permitted`
- **AND** the Problem Detail contains the positive `retryAfterSeconds`
- **AND** the REST contract does not require a `Retry-After` response header

#### Scenario: Application creation is unavailable
- **WHEN** settings, persistence, or authoritative creation fails operationally
- **THEN** Halo returns `503 Service Unavailable`
- **AND** `type` is `https://halo.run/probs/link-application-unavailable`
- **AND** the detail does not expose submitted fields or internal exception data

#### Scenario: Problem extensions remain narrow
- **WHEN** the plugin creates a REST Problem Detail
- **THEN** it does not add the removed Form JSON envelope's `status`, `code`, or `field` contract
- **AND** safe Chinese `detail` text remains display information rather than a machine identifier

### Requirement: Anonymous REST application scope is create-only
The system SHALL expose no anonymous status, update, cancellation, or idempotency resource as part
of this capability.

#### Scenario: Client retries after losing a response
- **WHEN** the first request persisted an application but the client did not receive its `201`
- **AND** the client submits the same URL again without an idempotency key
- **THEN** the retry follows the existing duplicate rules
- **AND** may return the duplicate `409` Problem Detail

#### Scenario: Client attempts to read an application
- **WHEN** an anonymous client attempts to retrieve an application by the returned `id`
- **THEN** this capability provides no anonymous read operation

### Requirement: REST application contracts are published through OpenAPI and theme documentation
The system SHALL publish the REST request, CAPTCHA, created-result, and Problem Details contracts
without replacing the native Form documentation.

#### Scenario: OpenAPI is generated
- **WHEN** the plugin OpenAPI document and TypeScript client are generated
- **THEN** both REST operations and their request and success schemas are present in the existing
  public Links API group

#### Scenario: Public documentation is read
- **WHEN** an integrator reads `dev/theme-api.md`
- **THEN** the native Form and REST contracts are documented in separate sections
- **AND** REST guidance includes cURL and browser `fetch` examples
- **AND** explains explicit challenge refresh, Halo-owned CORS, cookie-free calls, Problem type
  handling, and the absence of status lookup and idempotency
- **AND** does not require a platform-specific mini-program SDK
