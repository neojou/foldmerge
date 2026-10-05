package com.neojou.foldmerge

import com.neojou.foldmerge.model.WorkspaceAccess

/**
 * Desktop file system: choose a directory, list it, read text, and write text.
 */
expect fun platformWorkspaceAccess(): WorkspaceAccess
