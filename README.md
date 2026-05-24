This is the solution for "delivery booking" problem.

# Delivery booking

When a booking window opens, a large number of drivers may try to claim a limited number of
delivery opportunities at nearly the same time.

Design a backend system for this booking flow.

Your system should ensure that:
1. No overselling
- The total number of successful bookings for a delivery opportunity must never
  exceed its capacity.

2. Low latency under heavy concurrency
- The system should remain responsive when many drivers try to book at once.

3. Fault tolerance
- The system should continue operating correctly when individual components fail,
  requests are retried, or messages are delivered more than once.

# Setup

See [setup](./SETUP.md) for details how to run this project locally.

# Solution

Solution explanation is provided separately.