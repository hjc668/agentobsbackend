package com.icbc.aiops.langfuse.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.icbc.aiops.langfuse.postgres.mapper.UserRoleMapper;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

/** Authorization must default to least privilege in every ambiguous case. */
class AamRoleResolverTest {

    @Test
    void configuredResolverGrantsAdminOnlyToListedUsers() {
        AamRoleResolver resolver = new ConfiguredAamRoleResolver("admin,38971135");
        assertEquals(AamRole.ADMIN, resolver.resolve("38971135"));
        assertEquals(AamRole.VIEW, resolver.resolve("viewer-001"));
        assertEquals(AamRole.VIEW, resolver.resolve(""));
    }

    @Test
    void configuredResolverHandlesEmptyAndWhitespaceLists() {
        assertEquals(AamRole.VIEW, new ConfiguredAamRoleResolver("").resolve("anyone"));
        assertEquals(AamRole.VIEW, new ConfiguredAamRoleResolver(null).resolve("anyone"));
        assertEquals(AamRole.ADMIN, new ConfiguredAamRoleResolver(" a , b ").resolve("b"));
    }

    @Test
    void tableResolverReadsRolesCaseInsensitively() {
        assertEquals(AamRole.ADMIN, tableResolverReturning("ADMIN").resolve("38971135"));
        assertEquals(AamRole.ADMIN, tableResolverReturning("admin").resolve("38971135"));
        assertEquals(AamRole.VIEW, tableResolverReturning("VIEW").resolve("38971135"));
    }

    @Test
    void tableResolverDefaultsToViewWhenNoRowExists() {
        assertEquals(AamRole.VIEW, tableResolverReturning(null).resolve("unknown"));
    }

    @Test
    void tableResolverTreatsUnknownRoleCodeAsViewRatherThanEscalating() {
        assertEquals(AamRole.VIEW, tableResolverReturning("SUPERUSER").resolve("38971135"));
        assertEquals(AamRole.VIEW, tableResolverReturning("").resolve("38971135"));
    }

    @Test
    void tableResolverFailsClosedWhenTheRoleTableIsUnreachable() {
        UserRoleMapper failing = (UserRoleMapper) Proxy.newProxyInstance(
                UserRoleMapper.class.getClassLoader(), new Class<?>[] {UserRoleMapper.class},
                (proxy, method, args) -> { throw new QueryTimeoutException("database unavailable"); });
        // An unreachable authorization table must not grant ADMIN, and must not turn a
        // verified login into an authentication failure either.
        assertEquals(AamRole.VIEW, new TableAamRoleResolver(failing).resolve("38971135"));
    }

    @Test
    void tableResolverQueriesByTheNormalizedUserNumber() {
        AtomicReference<String> queried = new AtomicReference<String>();
        UserRoleMapper mapper = (UserRoleMapper) Proxy.newProxyInstance(
                UserRoleMapper.class.getClassLoader(), new Class<?>[] {UserRoleMapper.class},
                (proxy, method, args) -> { queried.set((String) args[0]); return "ADMIN"; });
        new TableAamRoleResolver(mapper).resolve("38971135");
        assertEquals("38971135", queried.get());
    }

    private static AamRoleResolver tableResolverReturning(String roleCode) {
        UserRoleMapper mapper = (UserRoleMapper) Proxy.newProxyInstance(
                UserRoleMapper.class.getClassLoader(), new Class<?>[] {UserRoleMapper.class},
                (proxy, method, args) -> roleCode);
        return new TableAamRoleResolver(mapper);
    }
}
