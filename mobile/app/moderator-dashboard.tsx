import React, { useState, useEffect, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  TextInput,
  Modal,
} from 'react-native';
import { useRouter } from 'expo-router';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { useAuth } from '../context/AuthContext';
import {
  getPendingReports,
  getReportStats,
  verifyReport,
  CommunityReportResponse,
  ReportStats,
  ReportType,
} from '../services/reports';

type FilterCategory = 'ALL' | 'LIGHTING' | 'ROAD_HAZARD' | 'PERSONAL_SAFETY' | 'OTHER';

const CATEGORY_MAP: Record<ReportType, { label: string; icon: string; group: FilterCategory }> = {
  POOR_LIGHTING: { label: 'Poor Lighting', icon: '🔦', group: 'LIGHTING' },
  BROKEN_STREETLIGHT: { label: 'Broken Streetlight', icon: '💡', group: 'LIGHTING' },
  ROAD_BLOCKAGE: { label: 'Road Blockage', icon: '🚧', group: 'ROAD_HAZARD' },
  UNSAFE_ROAD: { label: 'Unsafe Road', icon: '⚠️', group: 'ROAD_HAZARD' },
  CROWD_ISSUE: { label: 'Crowd Concern', icon: '👥', group: 'PERSONAL_SAFETY' },
  SUSPICIOUS_ACTIVITY: { label: 'Suspicious Activity', icon: '👁️', group: 'PERSONAL_SAFETY' },
  HARASSMENT: { label: 'Harassment', icon: '🛑', group: 'PERSONAL_SAFETY' },
  OTHER: { label: 'Other Hazard', icon: '📌', group: 'OTHER' },
};

export default function ModeratorDashboardScreen() {
  const router = useRouter();
  const { user } = useAuth();

  const [reports, setReports] = useState<CommunityReportResponse[]>([]);
  const [stats, setStats] = useState<ReportStats>({ pending: 0, verified: 0, rejected: 0 });
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activeFilter, setActiveFilter] = useState<FilterCategory>('ALL');

  // Action modal state
  const [selectedReport, setSelectedReport] = useState<CommunityReportResponse | null>(null);
  const [actionType, setActionType] = useState<'VERIFY' | 'REJECT' | null>(null);
  const [actionNote, setActionNote] = useState('');
  const [processingId, setProcessingId] = useState<string | null>(null);

  const fetchData = useCallback(async () => {
    setError(null);
    try {
      const [pendingRes, statsRes] = await Promise.all([
        getPendingReports(0, 50),
        getReportStats().catch(() => ({ pending: 0, verified: 0, rejected: 0 })),
      ]);
      setReports(pendingRes.content || []);
      setStats(statsRes);
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Failed to load moderation queue.';
      setError(msg);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleRefresh = () => {
    setRefreshing(true);
    fetchData();
  };

  const openActionModal = (report: CommunityReportResponse, type: 'VERIFY' | 'REJECT') => {
    setSelectedReport(report);
    setActionType(type);
    setActionNote(
      type === 'VERIFY'
        ? 'Verified by safety moderator. Active on public routing map.'
        : 'Report dismissed: insufficient detail or duplicate entry.'
    );
  };

  const closeActionModal = () => {
    setSelectedReport(null);
    setActionType(null);
    setActionNote('');
  };

  const handleConfirmAction = async () => {
    if (!selectedReport || !actionType) return;
    const targetStatus = actionType === 'VERIFY' ? 'VERIFIED' : 'REJECTED';
    setProcessingId(selectedReport.id);

    try {
      await verifyReport(selectedReport.id, {
        status: targetStatus,
        verificationNote: actionNote.trim() || undefined,
      });

      // Remove from pending list
      setReports((prev) => prev.filter((r) => r.id !== selectedReport.id));
      setStats((prev) => ({
        ...prev,
        pending: Math.max(0, prev.pending - 1),
        verified: targetStatus === 'VERIFIED' ? prev.verified + 1 : prev.verified,
        rejected: targetStatus === 'REJECTED' ? prev.rejected + 1 : prev.rejected,
      }));

      Alert.alert(
        targetStatus === 'VERIFIED' ? '✅ Report Approved' : '❌ Report Rejected',
        targetStatus === 'VERIFIED'
          ? 'The community report has been verified. SafePath AI has updated the routing cost matrix to avoid this location.'
          : 'The report was rejected and will not affect public pedestrian routes.'
      );
      closeActionModal();
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Moderation action failed.';
      Alert.alert('Action Failed', msg);
    } finally {
      setProcessingId(null);
    }
  };

  const filteredReports = reports.filter((r) => {
    if (activeFilter === 'ALL') return true;
    const meta = CATEGORY_MAP[r.reportType];
    return meta ? meta.group === activeFilter : false;
  });

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      {/* Header */}
      <View style={styles.headerRow}>
        <TouchableOpacity
          onPress={() => router.back()}
          style={styles.backBtn}
          accessibilityRole="button"
          accessibilityLabel="Go back"
        >
          <Text style={styles.backBtnText}>‹ Back</Text>
        </TouchableOpacity>
        <TouchableOpacity
          onPress={handleRefresh}
          style={styles.refreshBtn}
          accessibilityRole="button"
          accessibilityLabel="Refresh moderation queue"
        >
          <Text style={styles.refreshBtnText}>🔄 Refresh</Text>
        </TouchableOpacity>
      </View>

      <View style={styles.titleSection}>
        <View style={styles.titleBadgeRow}>
          <Text style={styles.titleBadge}>🛡️ MODERATOR DESK</Text>
        </View>
        <Text style={styles.title}>Safety Reports Moderation</Text>
        <Text style={styles.subtitle}>
          Review incoming hazard submissions. Approving a report automatically activates live route avoidance penalties in the A* routing engine.
        </Text>
      </View>

      {/* KPI Stats Cards */}
      <View style={styles.statsRow}>
        <View style={[styles.statCard, styles.statCardPending]}>
          <Text style={styles.statNumber}>{stats.pending}</Text>
          <Text style={styles.statLabel}>⏳ Pending</Text>
        </View>
        <View style={[styles.statCard, styles.statCardVerified]}>
          <Text style={styles.statNumber}>{stats.verified}</Text>
          <Text style={styles.statLabel}>✅ Verified</Text>
        </View>
        <View style={[styles.statCard, styles.statCardRejected]}>
          <Text style={styles.statNumber}>{stats.rejected}</Text>
          <Text style={styles.statLabel}>❌ Rejected</Text>
        </View>
      </View>

      {/* Filter Chips */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.filterScroll}
      >
        <TouchableOpacity
          style={[styles.filterChip, activeFilter === 'ALL' && styles.filterChipActive]}
          onPress={() => setActiveFilter('ALL')}
        >
          <Text
            style={[
              styles.filterChipText,
              activeFilter === 'ALL' && styles.filterChipTextActive,
            ]}
          >
            All Pending ({reports.length})
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.filterChip, activeFilter === 'LIGHTING' && styles.filterChipActive]}
          onPress={() => setActiveFilter('LIGHTING')}
        >
          <Text
            style={[
              styles.filterChipText,
              activeFilter === 'LIGHTING' && styles.filterChipTextActive,
            ]}
          >
            🔦 Lighting
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.filterChip, activeFilter === 'ROAD_HAZARD' && styles.filterChipActive]}
          onPress={() => setActiveFilter('ROAD_HAZARD')}
        >
          <Text
            style={[
              styles.filterChipText,
              activeFilter === 'ROAD_HAZARD' && styles.filterChipTextActive,
            ]}
          >
            🚧 Road Hazards
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.filterChip, activeFilter === 'PERSONAL_SAFETY' && styles.filterChipActive]}
          onPress={() => setActiveFilter('PERSONAL_SAFETY')}
        >
          <Text
            style={[
              styles.filterChipText,
              activeFilter === 'PERSONAL_SAFETY' && styles.filterChipTextActive,
            ]}
          >
            🛑 Safety Concerns
          </Text>
        </TouchableOpacity>
      </ScrollView>

      {/* Content State */}
      {loading && !refreshing && (
        <View style={styles.loadingBox}>
          <ActivityIndicator size="large" color="#007AFF" />
          <Text style={styles.loadingText}>Fetching pending moderation reports...</Text>
        </View>
      )}

      {error && !loading && (
        <Card style={styles.errorCard}>
          <Text style={styles.errorIcon}>⚠️</Text>
          <Text style={styles.errorTitle}>Queue Load Error</Text>
          <Text style={styles.errorText}>{error}</Text>
          <Button title="Try Again" onPress={fetchData} variant="secondary" />
        </Card>
      )}

      {!loading && !error && filteredReports.length === 0 && (
        <Card style={styles.emptyCard}>
          <Text style={styles.emptyIcon}>🎉</Text>
          <Text style={styles.emptyTitle}>All Caught Up!</Text>
          <Text style={styles.emptySubtitle}>
            There are no pending community reports waiting for review. New reports submitted by citizens will appear here in real time.
          </Text>
          <Button title="Refresh Queue" onPress={handleRefresh} />
        </Card>
      )}

      {!loading &&
        filteredReports.map((report) => {
          const cat = CATEGORY_MAP[report.reportType] || {
            label: report.reportType,
            icon: '📌',
            group: 'OTHER',
          };

          return (
            <Card key={report.id} style={styles.queueCard}>
              {/* Card Header */}
              <View style={styles.cardHeader}>
                <View style={styles.categoryBadge}>
                  <Text style={styles.categoryIcon}>{cat.icon}</Text>
                  <Text style={styles.categoryLabel}>{cat.label}</Text>
                </View>
                <View style={styles.pendingBadge}>
                  <Text style={styles.pendingBadgeText}>⏳ PENDING</Text>
                </View>
              </View>

              {/* Description */}
              <Text style={styles.reportDesc}>
                {report.description ? report.description : 'No additional text description provided.'}
              </Text>

              {/* Location Precision & Coordinates */}
              <View style={styles.metaRow}>
                <Text style={styles.metaDate}>
                  📅 {new Date(report.createdAt).toLocaleString([], {
                    month: 'short',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit',
                  })}
                </Text>

                <View style={styles.precisionBadge}>
                  <Text style={styles.precisionText}>
                    {report.locationPrecision === 'EXACT_COORDINATE'
                      ? `📍 GPS: ${report.latitude?.toFixed(4)}, ${report.longitude?.toFixed(4)}`
                      : '⚪ Locality (No GPS)'}
                  </Text>
                </View>
              </View>

              {/* Route Impact Simulation Box */}
              <View style={styles.impactPreviewBox}>
                <View style={styles.impactPreviewHeader}>
                  <Text style={styles.impactPreviewIcon}>⚡</Text>
                  <Text style={styles.impactPreviewTitle}>A* Routing Impact upon Approval</Text>
                </View>
                <Text style={styles.impactPreviewDesc}>
                  Approving this report immediately marks this road segment with a +35 safety penalty. Pedestrians on SAFEST and BALANCED modes will automatically be detoured along lit parallel paths (+110m, 100% lit).
                </Text>
              </View>

              {/* Action Buttons */}
              <View style={styles.actionBtnRow}>
                <TouchableOpacity
                  style={[styles.actionBtn, styles.approveBtn]}
                  onPress={() => openActionModal(report, 'VERIFY')}
                  accessibilityRole="button"
                  accessibilityLabel="Approve and verify report"
                >
                  <Text style={styles.approveBtnText}>✅ Approve Report</Text>
                </TouchableOpacity>

                <TouchableOpacity
                  style={[styles.actionBtn, styles.rejectBtn]}
                  onPress={() => openActionModal(report, 'REJECT')}
                  accessibilityRole="button"
                  accessibilityLabel="Reject report"
                >
                  <Text style={styles.rejectBtnText}>❌ Reject</Text>
                </TouchableOpacity>
              </View>
            </Card>
          );
        })}

      {/* Confirmation & Note Modal */}
      <Modal
        visible={!!selectedReport && !!actionType}
        transparent
        animationType="fade"
        onRequestClose={closeActionModal}
      >
        <View style={styles.modalOverlay}>
          <Card style={styles.modalCard}>
            <Text style={styles.modalTitle}>
              {actionType === 'VERIFY' ? 'Approve Community Report' : 'Reject Community Report'}
            </Text>
            <Text style={styles.modalSubtitle}>
              {actionType === 'VERIFY'
                ? 'This report will become active on the public safety map and trigger routing avoidance detours.'
                : 'This report will be archived as rejected and will not affect pedestrian routing.'}
            </Text>

            <Text style={styles.modalInputLabel}>Moderator Note (Visible to Reporter):</Text>
            <TextInput
              style={styles.modalTextInput}
              multiline
              value={actionNote}
              onChangeText={setActionNote}
              placeholder="Enter note or reason..."
              placeholderTextColor="#8E8E93"
            />

            <View style={styles.modalBtnRow}>
              <TouchableOpacity
                style={[styles.modalBtn, styles.modalCancelBtn]}
                onPress={closeActionModal}
                disabled={!!processingId}
              >
                <Text style={styles.modalCancelBtnText}>Cancel</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={[
                  styles.modalBtn,
                  actionType === 'VERIFY' ? styles.modalApproveBtn : styles.modalRejectBtn,
                ]}
                onPress={handleConfirmAction}
                disabled={!!processingId}
              >
                {processingId ? (
                  <ActivityIndicator size="small" color="#FFFFFF" />
                ) : (
                  <Text style={styles.modalActionBtnText}>
                    {actionType === 'VERIFY' ? 'Confirm Approval' : 'Confirm Rejection'}
                  </Text>
                )}
              </TouchableOpacity>
            </View>
          </Card>
        </View>
      </Modal>
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
  headerRow: {
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
  titleBadgeRow: {
    flexDirection: 'row',
    marginBottom: 4,
  },
  titleBadge: {
    fontSize: 11,
    fontWeight: '800',
    color: '#007AFF',
    backgroundColor: '#E5F1FF',
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
  statsRow: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 16,
  },
  statCard: {
    flex: 1,
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 12,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 1,
  },
  statCardPending: {
    borderTopWidth: 3,
    borderTopColor: '#FF9500',
  },
  statCardVerified: {
    borderTopWidth: 3,
    borderTopColor: '#34C759',
  },
  statCardRejected: {
    borderTopWidth: 3,
    borderTopColor: '#8E8E93',
  },
  statNumber: {
    fontSize: 20,
    fontWeight: '800',
    color: '#1C1C1E',
  },
  statLabel: {
    fontSize: 11,
    fontWeight: '600',
    color: '#636366',
    marginTop: 2,
  },
  filterScroll: {
    gap: 8,
    paddingBottom: 14,
  },
  filterChip: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    backgroundColor: '#E5E5EA',
    borderRadius: 20,
  },
  filterChipActive: {
    backgroundColor: '#007AFF',
  },
  filterChipText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#3A3A3C',
  },
  filterChipTextActive: {
    color: '#FFFFFF',
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
  queueCard: {
    padding: 16,
    marginBottom: 12,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  categoryBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F2F2F7',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  categoryIcon: {
    fontSize: 15,
    marginRight: 6,
  },
  categoryLabel: {
    fontSize: 12,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  pendingBadge: {
    backgroundColor: '#FFF2D6',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  pendingBadgeText: {
    fontSize: 10,
    fontWeight: '800',
    color: '#D97706',
  },
  reportDesc: {
    fontSize: 14,
    color: '#1C1C1E',
    lineHeight: 20,
    marginBottom: 10,
  },
  metaRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#E5E5EA',
    paddingTop: 8,
  },
  metaDate: {
    fontSize: 11,
    color: '#8E8E93',
  },
  precisionBadge: {
    backgroundColor: '#F2F2F7',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  precisionText: {
    fontSize: 11,
    color: '#636366',
    fontWeight: '600',
  },
  impactPreviewBox: {
    backgroundColor: '#F8F9FA',
    borderRadius: 8,
    padding: 10,
    borderLeftWidth: 3,
    borderLeftColor: '#007AFF',
    marginBottom: 14,
  },
  impactPreviewHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  impactPreviewIcon: {
    fontSize: 14,
    marginRight: 4,
  },
  impactPreviewTitle: {
    fontSize: 12,
    fontWeight: '700',
    color: '#007AFF',
  },
  impactPreviewDesc: {
    fontSize: 11,
    color: '#48484A',
    lineHeight: 15,
  },
  actionBtnRow: {
    flexDirection: 'row',
    gap: 10,
  },
  actionBtn: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center',
  },
  approveBtn: {
    backgroundColor: '#34C759',
  },
  approveBtnText: {
    color: '#FFFFFF',
    fontWeight: '700',
    fontSize: 13,
  },
  rejectBtn: {
    backgroundColor: '#E5E5EA',
  },
  rejectBtnText: {
    color: '#FF3B30',
    fontWeight: '700',
    fontSize: 13,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  modalCard: {
    width: '100%',
    padding: 20,
  },
  modalTitle: {
    fontSize: 17,
    fontWeight: '800',
    color: '#1C1C1E',
    marginBottom: 6,
  },
  modalSubtitle: {
    fontSize: 12,
    color: '#636366',
    lineHeight: 16,
    marginBottom: 14,
  },
  modalInputLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#3A3A3C',
    marginBottom: 6,
  },
  modalTextInput: {
    backgroundColor: '#F2F2F7',
    borderRadius: 8,
    padding: 10,
    fontSize: 13,
    color: '#1C1C1E',
    minHeight: 70,
    textAlignVertical: 'top',
    marginBottom: 16,
  },
  modalBtnRow: {
    flexDirection: 'row',
    gap: 10,
  },
  modalBtn: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 8,
    alignItems: 'center',
  },
  modalCancelBtn: {
    backgroundColor: '#E5E5EA',
  },
  modalCancelBtnText: {
    color: '#3A3A3C',
    fontWeight: '600',
    fontSize: 13,
  },
  modalApproveBtn: {
    backgroundColor: '#34C759',
  },
  modalRejectBtn: {
    backgroundColor: '#FF3B30',
  },
  modalActionBtnText: {
    color: '#FFFFFF',
    fontWeight: '700',
    fontSize: 13,
  },
});
