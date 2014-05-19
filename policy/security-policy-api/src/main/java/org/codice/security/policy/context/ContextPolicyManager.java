/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
package org.codice.security.policy.context;

import java.util.Collection;

/**
 * Manages and stores all web context policies.
 */
public interface ContextPolicyManager {

    public ContextPolicy getContextPolicy(String path);

    public Collection<ContextPolicy> getAllContextPolicies();

    public void setContextPolicy(String path, ContextPolicy contextPolicy);
}
