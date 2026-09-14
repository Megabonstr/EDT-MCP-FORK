/**
 * MCP Server for EDT
 * Copyright (C) 2025 DitriX (https://github.com/DitriXNew)
 * Licensed under AGPL-3.0-or-later
 */

package com.ditrix.edt.mcp.server.utils;

/** Controlled failure at the optional Code Review OSGi integration boundary. */
public class CodeReviewBridgeException extends Exception
{
    private static final long serialVersionUID = 1L;

    public CodeReviewBridgeException(String message)
    {
        super(message);
    }

    public CodeReviewBridgeException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
