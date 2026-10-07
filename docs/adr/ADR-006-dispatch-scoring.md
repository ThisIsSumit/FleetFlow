# ADR: Dispatch scoring algorithm

## Context
Candidates within the radius must be ranked consistently, and each organization wants to tune priorities.

## Decision
A deterministic weighted sum of five 0..1 scores: distance, availability, workload, vehicle match, rating. Weights are stored per organization and normalised at use time. Ties break on distance then driver id, so a given input always ranks identically. It is not machine learning and is documented as such.

## Alternatives
Nearest-driver-only (ignores workload/rating); ML ranking (needs data we do not have and is hard to explain); auction/bidding by drivers.

## Trade-offs
Easy to explain and test, but the weights are hand-tuned and the model ignores traffic, road distance (it uses straight-line distance) and delivery deadlines.
