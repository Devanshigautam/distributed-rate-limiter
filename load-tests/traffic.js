// Steady traffic through the load balancer: 60 requests/second for 2 minutes,
// spread over free-tier clients (token bucket) and one premium client (sliding window).
import http from 'k6/http';

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-arrival-rate',
      rate: 60,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 20,
      maxVUs: 60,
    },
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

const clients = ['devanshi', 'rahul', 'amit', 'sara', 'priya', 'priya'];   // priya = premium

export default function () {
  const client = clients[Math.floor(Math.random() * clients.length)];
  http.get('http://lb/api/orders', {
    headers: { 'X-Client-Id': client },
    responseCallback: http.expectedStatuses(200, 429),   // a 429 is the limiter working, not an error
  });
}
