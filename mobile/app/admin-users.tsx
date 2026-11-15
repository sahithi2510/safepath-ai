import React, { useState, useEffect, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  TextInput,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { useRouter } from 'expo-router';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { useAuth } from '../context/AuthContext';
import {
  listAdminUsers,
  updateUserRole,
  AdminUserSummary,
} from '../services/admin';

export default function AdminUsersScreen() {
  const router = useRouter();
  const { user } = useAuth();

  const [users, setUsers] = useState<AdminUserSummary[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [updatingId, setUpdatingId] = useState<string | null>(null);

  const fetchUsers = useCallback(async (query?: string) => {
    setError(null);
    try {
      const data = await listAdminUsers(query, 0, 50);
      setUsers(data.content || []);
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Unable to load users.';
      setError(msg);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchUsers(searchQuery);
  }, [fetchUsers]);

  const handleSearch = () => {
    setLoading(true);
    fetchUsers(searchQuery);
  };

  const handleToggleModerator = (targetUser: AdminUserSummary) => {
    const isMod = targetUser.roles?.includes('ROLE_MODERATOR');
    const action = isMod ? 'REMOVE' : 'ADD';
    const actionText = isMod ? 'Revoke Moderator Role' : 'Promote to Moderator';
    const confirmMessage = isMod
      ? `Revoke moderator privileges from ${targetUser.fullName || targetUser.email}? They will no longer be able to approve or reject community safety hazards.`
      : `Promote ${targetUser.fullName || targetUser.email} to Moderator? They will receive access to the Moderator Desk and can review community reports.`;

    Alert.alert(actionText, confirmMessage, [
      { text: 'Cancel', style: 'cancel' },
      {
        text: actionText,
        style: isMod ? 'destructive' : 'default',
        onPress: async () => {
          setUpdatingId(targetUser.id);
          try {
            const updated = await updateUserRole(targetUser.id, {
              role: 'ROLE_MODERATOR',
              action,
            });
            setUsers((prev) =>
              prev.map((u) => (u.id === targetUser.id ? updated : u))
            );
            Alert.alert(
              'Role Updated',
              isMod
                ? `${targetUser.fullName || targetUser.email} is now a regular user.`
                : `${targetUser.fullName || targetUser.email} has been granted Moderator privileges.`
            );
          } catch (err: any) {
            const msg =
              err.response?.data?.message || err.message || 'Failed to update user role.';
            Alert.alert('Update Failed', msg);
          } finally {
            setUpdatingId(null);
          }
        },
      },
    ]);
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      {/* Top Navigation */}
      <View style={styles.navRow}>
        <TouchableOpacity
          onPress={() => router.back()}
          style={styles.backBtn}
          accessibilityRole="button"
          accessibilityLabel="Go back"
        >
          <Text style={styles.backBtnText}>‹ Back to Profile</Text>
        </TouchableOpacity>
        <TouchableOpacity
          onPress={() => {
            setRefreshing(true);
            fetchUsers(searchQuery);
          }}
          style={styles.refreshBtn}
        >
          <Text style={styles.refreshBtnText}>🔄 Refresh</Text>
        </TouchableOpacity>
      </View>

      {/* Screen Title */}
      <View style={styles.titleSection}>
        <View style={styles.badgeRow}>
          <Text style={styles.adminBadge}>👑 ADMIN ONLY</Text>
        </View>
        <Text style={styles.title}>Team & Role Controls</Text>
        <Text style={styles.subtitle}>
          Control who can access moderator tools. Promote trusted individuals to review incoming safety reports or revoke privileges instantly.
        </Text>
      </View>

      {/* Search Bar */}
      <View style={styles.searchContainer}>
        <TextInput
          style={styles.searchInput}
          placeholder="Search by name or email address..."
          placeholderTextColor="#8E8E93"
          value={searchQuery}
          onChangeText={setSearchQuery}
          onSubmitEditing={handleSearch}
          returnKeyType="search"
          autoCapitalize="none"
        />
        <TouchableOpacity style={styles.searchBtn} onPress={handleSearch}>
          <Text style={styles.searchBtnText}>Search</Text>
        </TouchableOpacity>
      </View>

      {/* States */}
      {loading && !refreshing && (
        <View style={styles.loadingBox}>
          <ActivityIndicator size="large" color="#007AFF" />
          <Text style={styles.loadingText}>Fetching users from secure directory...</Text>
        </View>
      )}

      {error && !loading && (
        <Card style={styles.errorCard}>
          <Text style={styles.errorIcon}>⚠️</Text>
          <Text style={styles.errorTitle}>Directory Error</Text>
          <Text style={styles.errorText}>{error}</Text>
          <Button title="Try Again" onPress={() => fetchUsers(searchQuery)} variant="secondary" />
        </Card>
      )}

      {!loading && !error && users.length === 0 && (
        <Card style={styles.emptyCard}>
          <Text style={styles.emptyIcon}>🔍</Text>
          <Text style={styles.emptyTitle}>No Users Found</Text>
          <Text style={styles.emptySubtitle}>
            No registered users match your search query. Clear search to list all members.
          </Text>
          <Button
            title="Clear Search"
            onPress={() => {
              setSearchQuery('');
              fetchUsers('');
            }}
          />
        </Card>
      )}

      {/* User Cards */}
      {!loading &&
        users.map((item) => {
          const isAdmin = item.roles?.includes('ROLE_ADMIN');
          const isMod = item.roles?.includes('ROLE_MODERATOR');
          const isSelf = user?.email?.toLowerCase() === item.email?.toLowerCase();

          return (
            <Card key={item.id} style={styles.userCard}>
              <View style={styles.userCardHeader}>
                <View style={styles.avatarMini}>
                  <Text style={styles.avatarMiniText}>
                    {(item.fullName || item.email).charAt(0).toUpperCase()}
                  </Text>
                </View>
                <View style={styles.userInfoCol}>
                  <View style={styles.nameRow}>
                    <Text style={styles.userName}>{item.fullName || 'Anonymous User'}</Text>
                    {isSelf && <Text style={styles.youBadge}>(You)</Text>}
                  </View>
                  <Text style={styles.userEmail}>{item.email}</Text>
                  {item.phoneNumber && (
                    <Text style={styles.userPhone}>📞 {item.phoneNumber}</Text>
                  )}
                </View>
              </View>

              {/* Roles Badges */}
              <View style={styles.rolesRow}>
                <Text style={styles.rolesLabel}>Active Roles:</Text>
                <View style={styles.rolesPillContainer}>
                  {isAdmin && (
                    <View style={[styles.rolePill, styles.roleAdminPill]}>
                      <Text style={styles.roleAdminText}>👑 Root Admin</Text>
                    </View>
                  )}
                  {isMod && (
                    <View style={[styles.rolePill, styles.roleModPill]}>
                      <Text style={styles.roleModText}>🛡️ Moderator</Text>
                    </View>
                  )}
                  {!isAdmin && !isMod && (
                    <View style={[styles.rolePill, styles.roleUserPill]}>
                      <Text style={styles.roleUserText}>👤 Citizen User</Text>
                    </View>
                  )}
                </View>
              </View>

              {/* Actions */}
              <View style={styles.actionsRow}>
                {isAdmin ? (
                  <View style={styles.protectedBox}>
                    <Text style={styles.protectedText}>
                      🔒 System Administrator (Permissions cannot be changed)
                    </Text>
                  </View>
                ) : (
                  <TouchableOpacity
                    style={[
                      styles.toggleBtn,
                      isMod ? styles.demoteBtn : styles.promoteBtn,
                    ]}
                    onPress={() => handleToggleModerator(item)}
                    disabled={updatingId === item.id}
                  >
                    {updatingId === item.id ? (
                      <ActivityIndicator size="small" color="#FFFFFF" />
                    ) : (
                      <Text style={styles.toggleBtnText}>
                        {isMod ? '❌ Revoke Moderator Role' : '🛡️ Promote to Moderator'}
                      </Text>
                    )}
                  </TouchableOpacity>
                )}
              </View>
            </Card>
          );
        })}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F2F2F7',
  },
  contentContainer: {
    padding: 16,
    paddingTop: 48,
    paddingBottom: 40,
  },
  navRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  backBtn: {
    paddingVertical: 6,
    paddingHorizontal: 10,
    backgroundColor: '#E5E5EA',
    borderRadius: 8,
  },
  backBtnText: {
    fontSize: 14,
    fontWeight: '700',
    color: '#007AFF',
  },
  refreshBtn: {
    paddingVertical: 6,
    paddingHorizontal: 10,
    backgroundColor: '#E5E5EA',
    borderRadius: 8,
  },
  refreshBtnText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#007AFF',
  },
  titleSection: {
    marginBottom: 16,
  },
  badgeRow: {
    flexDirection: 'row',
    marginBottom: 4,
  },
  adminBadge: {
    fontSize: 11,
    fontWeight: '800',
    color: '#7C3AED',
    backgroundColor: '#F3E8FF',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  title: {
    fontSize: 24,
    fontWeight: '800',
    color: '#1C1C1E',
  },
  subtitle: {
    fontSize: 13,
    color: '#636366',
    marginTop: 4,
    lineHeight: 18,
  },
  searchContainer: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 16,
  },
  searchInput: {
    flex: 1,
    backgroundColor: '#FFFFFF',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 14,
    color: '#1C1C1E',
    borderWidth: 1,
    borderColor: '#E5E5EA',
  },
  searchBtn: {
    backgroundColor: '#7C3AED',
    paddingHorizontal: 16,
    borderRadius: 10,
    justifyContent: 'center',
    alignItems: 'center',
  },
  searchBtnText: {
    color: '#FFFFFF',
    fontWeight: '700',
    fontSize: 13,
  },
  loadingBox: {
    padding: 40,
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 13,
    color: '#8E8E93',
  },
  errorCard: {
    padding: 20,
    alignItems: 'center',
  },
  errorIcon: {
    fontSize: 32,
    marginBottom: 8,
  },
  errorTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#FF3B30',
    marginBottom: 4,
  },
  errorText: {
    fontSize: 13,
    color: '#636366',
    textAlign: 'center',
    marginBottom: 14,
  },
  emptyCard: {
    padding: 30,
    alignItems: 'center',
  },
  emptyIcon: {
    fontSize: 40,
    marginBottom: 10,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '800',
    color: '#1C1C1E',
    marginBottom: 6,
  },
  emptySubtitle: {
    fontSize: 13,
    color: '#636366',
    textAlign: 'center',
    lineHeight: 18,
    marginBottom: 16,
  },
  userCard: {
    padding: 16,
    marginBottom: 12,
  },
  userCardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  avatarMini: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: '#E5E5EA',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  avatarMiniText: {
    fontSize: 18,
    fontWeight: '700',
    color: '#007AFF',
  },
  userInfoCol: {
    flex: 1,
  },
  nameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  userName: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  youBadge: {
    fontSize: 11,
    color: '#7C3AED',
    fontWeight: '700',
  },
  userEmail: {
    fontSize: 13,
    color: '#636366',
    marginTop: 1,
  },
  userPhone: {
    fontSize: 11,
    color: '#8E8E93',
    marginTop: 2,
  },
  rolesRow: {
    flexDirection: 'row',
    alignItems: 'center',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#E5E5EA',
    paddingTop: 10,
    marginBottom: 12,
  },
  rolesLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#8E8E93',
    marginRight: 8,
  },
  rolesPillContainer: {
    flexDirection: 'row',
    gap: 6,
    flexWrap: 'wrap',
  },
  rolePill: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  roleAdminPill: {
    backgroundColor: '#F3E8FF',
  },
  roleAdminText: {
    color: '#7C3AED',
    fontSize: 11,
    fontWeight: '700',
  },
  roleModPill: {
    backgroundColor: '#D1FAE5',
  },
  roleModText: {
    color: '#059669',
    fontSize: 11,
    fontWeight: '700',
  },
  roleUserPill: {
    backgroundColor: '#F2F2F7',
  },
  roleUserText: {
    color: '#636366',
    fontSize: 11,
    fontWeight: '600',
  },
  actionsRow: {
    marginTop: 4,
  },
  protectedBox: {
    backgroundColor: '#F8F9FA',
    padding: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#E5E5EA',
    alignItems: 'center',
  },
  protectedText: {
    fontSize: 11,
    color: '#636366',
    fontStyle: 'italic',
  },
  toggleBtn: {
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center',
  },
  promoteBtn: {
    backgroundColor: '#059669',
  },
  demoteBtn: {
    backgroundColor: '#E5E5EA',
    borderWidth: 1,
    borderColor: '#FF3B30',
  },
  toggleBtnText: {
    color: '#FFFFFF',
    fontWeight: '700',
    fontSize: 13,
  },
});
