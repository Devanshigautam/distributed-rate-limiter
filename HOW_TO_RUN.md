# How to run (Windows)

1. Start Docker Desktop, then in the IntelliJ Terminal:
       docker compose up -d
2. Run DistributedRateLimiterApplication (green arrow).
3. Health check in the browser:  http://localhost:8080/actuator/health   (redis should be UP)
4. Send 15 quick requests in Command Prompt:
       for /L %i in (1,1,15) do @curl.exe -s -o NUL -w "%{http_code} " -H "X-Client-Id: devanshi" http://localhost:8080/api/orders
   Expected: about ten 200s, then 429s.
5. See the headers of one request:
       curl.exe -i -H "X-Client-Id: devanshi" http://localhost:8080/api/orders
