package com.nahid.booking.credits;

import java.util.List;

public record CreditStatementResponse(Long userId, long balance, List<CreditEntryResponse> entries) {}
