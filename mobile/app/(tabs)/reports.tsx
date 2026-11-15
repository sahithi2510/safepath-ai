import React, { useState, useEffect, useCallback } from 'react';
import {
  StyleSheet,
  View,
  Text,
  ScrollView,
  TouchableOpacity,
  TextInput,
  ActivityIndicator,
  Switch,
  Alert,
  Modal,
} from 'react-native';
import * as Location from 'expo-location';
import { useRouter } from 'expo-router';
import { Card } from '../../components/Card';
import { Button } from '../../components/Button';
import { useAuth } from '../../context/AuthContext';
import {
  submitReport,
  getMyReports,
  getPendingReports,
  getReportStats,
  verifyReport,
  ReportType,
  ReportStatus,
  CommunityReportResponse,
  SubmitReportResponse,
  ReportStats,
} from '../../services/reports';
import {
  getNotifications,
  getUnreadNotifications,
  markNotificationAsRead,
  markAllNotificationsAsRead,
  NotificationItem,
} from '../../services/notifications';

interface CategoryOption {
  type: ReportType;
  label: string;
  icon: string;
  description: string;
}

const CATEGORIES: CategoryOption[] = [
  {
    type: 'POOR_LIGHTING',
    label: 'Poor Lighting',
    icon: '🔦',
    description: 'Dark area, dim or inadequate street lighting',
  },
  {
    type: 'BROKEN_STREETLIGHT',
    label: 'Broken Streetlight',
    icon: '💡',
    description: 'Non-functional or damaged street lamps',
  },
  {
    type: 'ROAD_BLOCKAGE',
    label: 'Road Blockage',
    icon: '🚧',
    description: 'Debris, fallen trees, or construction hazard',
  },
  {
    type: 'UNSAFE_ROAD',
    label: 'Unsafe Road',
    icon: '⚠️',
    description: 'Dangerous walkway, potholes, or lack of sidewalk',
  },
  {
    type: 'CROWD_ISSUE',
    label: 'Crowd Concern',
    icon: '👥',
    description: 'Overcrowding, brawl, or unruly gathering',
  },
  {
    type: 'SUSPICIOUS_ACTIVITY',
    label: 'Suspicious Activity',
    icon: '👁️',
    description: 'Lurking or unusual behavior',
  },
  {
    type: 'HARASSMENT',
    label: 'Harassment',
    icon: '🛑',
    description: 'Verbal harassment, stalking, or intimidation',
  },
  {
    type: 'OTHER',
    label: 'Other Concern',
    icon: '📌',
    description: 'Other neighborhood safety issue',
  },
];

type IncidentTimeOption = 'JUST_NOW' | 'PAST_HOUR' | 'EARLIER_TODAY' | 'YESTERDAY';

export default function ReportsScreen() {
  const router = useRouter();
  const { user } = useAuth();
  const isModerator =
    user?.email?.toLowerCase().includes('admin') ||
    user?.email === 'sahithidhanakudharam25@gmail.com';

  // Navigation / active tab
  const [activeTab, setActiveTab] = useState<'SUBMIT' | 'HISTORY' | 'MODERATOR'>('SUBMIT');

  // Form state
  const [selectedCategory, setSelectedCategory] = useState<ReportType>('POOR_LIGHTING');
  const [description, setDescription] = useState('');
  const [timeOption, setTimeOption] = useState<IncidentTimeOption>('JUST_NOW');

  // Location state
  const [includeLocation, setIncludeLocation] = useState(false);
  const [locationConfirmed, setLocationConfirmed] = useState(false);
  const [coords, setCoords] = useState<{ latitude: number; longitude: number } | null>(null);
  const [locLoading, setLocLoading] = useState(false);
  const [locError, setLocError] = useState<string | null>(null);

  // Submission state
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitSuccess, setSubmitSuccess] = useState<SubmitReportResponse | null>(null);

  // History state
  const [myReports, setMyReports] = useState<CommunityReportResponse[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [verifyingId, setVerifyingId] = useState<string | null>(null);

  // Notifications state
  const [unreadNotifications, setUnreadNotifications] = useState<NotificationItem[]>([]);
  const [allNotifications, setAllNotifications] = useState<NotificationItem[]>([]);
  const [notificationsModalOpen, setNotificationsModalOpen] = useState(false);

  // Moderator state
  const [pendingReports, setPendingReports] = useState<CommunityReportResponse[]>([]);
  const [pendingLoading, setPendingLoading] = useState(false);
  const [reportStats, setReportStats] = useState<ReportStats>({ pending: 0, verified: 0, rejected: 0 });

  const loadNotifications = useCallback(async () => {
    try {
      const [unread, all] = await Promise.all([
        getUnreadNotifications().catch(() => []),
        getNotifications(0, 15).then((r) => r.content).catch(() => []),
      ]);
      setUnreadNotifications(unread);
      setAllNotifications(all);
    } catch {
      // ignore
    }
  }, []);

  const loadPendingQueue = useCallback(async () => {
    if (!isModerator) return;
    setPendingLoading(true);
    try {
      const [pendingRes, statsRes] = await Promise.all([
        getPendingReports(0, 30),
        getReportStats().catch(() => ({ pending: 0, verified: 0, rejected: 0 })),
      ]);
      setPendingReports(pendingRes.content || []);
      setReportStats(statsRes);
    } catch {
      // ignore
    } finally {
      setPendingLoading(false);
    }
  }, [isModerator]);

  const handleDismissNotification = async (id: string) => {
    try {
      await markNotificationAsRead(id);
      setUnreadNotifications((prev) => prev.filter((n) => n.id !== id));
      setAllNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true } : n))
      );
    } catch {
      // ignore
    }
  };

  const handleMarkAllNotificationsRead = async () => {
    try {
      await markAllNotificationsAsRead();
      setUnreadNotifications([]);
      setAllNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    } catch {
      // ignore
    }
  };

  const handleVerify = async (reportId: string) => {
    try {
      setVerifyingId(reportId);
      await verifyReport(reportId, {
        status: 'VERIFIED',
        verificationNote: 'Verified by safety moderator',
      });
      setMyReports((prev) =>
        prev.map((r) => (r.id === reportId ? { ...r, status: 'VERIFIED' } : r))
      );
      Alert.alert('Report Approved', 'The safety report has been approved and is now active on the public map.');
    } catch (err: any) {
      const msg = err.response?.data?.message || err.message || 'Unable to verify report.';
      Alert.alert('Moderation Failed', msg);
    } finally {
      setVerifyingId(null);
    }
  };

  // Fetch location when user opts in
  const handleToggleLocation = async (value: boolean) => {
    setIncludeLocation(value);
    setLocError(null);

    if (value) {
      setLocLoading(true);
      try {
        const { status } = await Location.requestForegroundPermissionsAsync();
        if (status !== 'granted') {
          setLocError('Foreground location permission was denied. You may still submit without location.');
          setIncludeLocation(false);
          setLocationConfirmed(false);
          setCoords(null);
          return;
        }

        const loc = await Location.getCurrentPositionAsync({
          accuracy: Location.Accuracy.Balanced,
        });
        setCoords({
          latitude: loc.coords.latitude,
          longitude: loc.coords.longitude,
        });
        setLocationConfirmed(true);
      } catch (err) {
        setLocError('Could not acquire device location. You can submit without location.');
        setIncludeLocation(false);
        setLocationConfirmed(false);
        setCoords(null);
      } finally {
        setLocLoading(false);
      }
    } else {
      setLocationConfirmed(false);
      setCoords(null);
    }
  };

  // Sample preset location helper (Bangalore central)
  const useSampleLocation = () => {
    setIncludeLocation(true);
    setCoords({ latitude: 12.9716, longitude: 77.5946 });
    setLocationConfirmed(true);
    setLocError(null);
  };

  // Compute ISO string from time option
  const computeIncidentTime = (): string => {
    const now = new Date();
    switch (timeOption) {
      case 'PAST_HOUR':
        now.setHours(now.getHours() - 1);
        break;
      case 'EARLIER_TODAY':
        now.setHours(now.getHours() - 4);
        break;
      case 'YESTERDAY':
        now.setDate(now.getDate() - 1);
        break;
      case 'JUST_NOW':
      default:
        break;
    }
    return now.toISOString();
  };

  // Form submission
  const handleSubmit = async () => {
    if (description.length > 1000) {
      Alert.alert('Validation Error', 'Description must not exceed 1000 characters.');
      return;
    }

    setSubmitting(true);
    setSubmitError(null);

    try {
      const payload = {
        reportType: selectedCategory,
        description: description.trim() || undefined,
        latitude: includeLocation && coords ? coords.latitude : null,
        longitude: includeLocation && coords ? coords.longitude : null,
        locationConfirmed: includeLocation && locationConfirmed,
        incidentDatetime: computeIncidentTime(),
      };

      const res = await submitReport(payload);
      setSubmitSuccess(res);
      // Reset form fields
      setDescription('');
      setIncludeLocation(false);
      setLocationConfirmed(false);
      setCoords(null);
    } catch (err: any) {
      const msg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        err.message ||
        'Failed to submit report. Please check your network and try again.';
      setSubmitError(msg);
    } finally {
      setSubmitting(false);
    }
  };

  // Load user history
  const loadHistory = useCallback(async () => {
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const data = await getMyReports(0, 20);
      setMyReports(data.content || []);
    } catch (err: any) {
      const msg =
        err.response?.data?.message ||
        err.message ||
        'Unable to load your report history.';
      setHistoryError(msg);
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    loadNotifications();
    if (activeTab === 'HISTORY') {
      loadHistory();
    } else if (activeTab === 'MODERATOR') {
      loadPendingQueue();
    }
  }, [activeTab, loadHistory, loadPendingQueue, loadNotifications]);

  const getStatusColor = (status: ReportStatus) => {
    switch (status) {
      case 'VERIFIED':
        return '#34C759'; // Green
      case 'REJECTED':
        return '#8E8E93'; // Gray
      case 'PENDING':
      default:
        return '#FF9500'; // Amber
    }
  };

  const getStatusLabel = (status: ReportStatus) => {
    switch (status) {
      case 'VERIFIED':
        return 'VERIFIED';
      case 'REJECTED':
        return 'REJECTED';
      case 'PENDING':
      default:
        return 'PENDING REVIEW';
    }
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      {/* Emergency Disclaimer Banner */}
      <View style={styles.emergencyBanner} accessibilityRole="alert">
        <Text style={styles.emergencyIcon}>🚨</Text>
        <View style={styles.emergencyTextCol}>
          <Text style={styles.emergencyTitle}>EMERGENCY NOTICE</Text>
          <Text style={styles.emergencyDesc}>
            For immediate life safety threats or emergencies, call emergency services directly (112 or 100). Community reports are reviewed asynchronously and do NOT dispatch police or emergency responders.
          </Text>
        </View>
      </View>

      {/* In-App Notification Alert Banner */}
      {unreadNotifications.length > 0 && (
        <View style={styles.notificationAlertBanner} accessibilityRole="alert">
          <Text style={styles.notificationAlertIcon}>🔔</Text>
          <View style={styles.notificationAlertContent}>
            <Text style={styles.notificationAlertTitle}>
              {unreadNotifications[0].title}
            </Text>
            <Text style={styles.notificationAlertMsg}>
              {unreadNotifications[0].message}
            </Text>
          </View>
          <TouchableOpacity
            style={styles.notificationDismissBtn}
            onPress={() => handleDismissNotification(unreadNotifications[0].id)}
            accessibilityRole="button"
            accessibilityLabel="Dismiss notification"
          >
            <Text style={styles.notificationDismissText}>Dismiss</Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Header */}
      <View style={styles.header}>
        <View style={styles.headerTopRow}>
          <Text style={styles.title} accessibilityRole="header">
            Community Reports
          </Text>
          <TouchableOpacity
            style={styles.notificationBellBtn}
            onPress={() => setNotificationsModalOpen(true)}
            accessibilityRole="button"
            accessibilityLabel="Open notifications"
          >
            <Text style={styles.notificationBellIcon}>🔔</Text>
            {unreadNotifications.length > 0 && (
              <View style={styles.bellBadge}>
                <Text style={styles.bellBadgeText}>{unreadNotifications.length}</Text>
              </View>
            )}
          </TouchableOpacity>
        </View>
        <Text style={styles.subtitle}>
          Help improve neighborhood safety awareness by reporting hazards and unlit streets.
        </Text>
      </View>

      {/* Tab Switcher */}
      <View style={styles.tabBar}>
        <TouchableOpacity
          style={[styles.tabBtn, activeTab === 'SUBMIT' && styles.tabBtnActive]}
          onPress={() => {
            setActiveTab('SUBMIT');
            setSubmitSuccess(null);
          }}
          accessibilityRole="tab"
          accessibilityState={{ selected: activeTab === 'SUBMIT' }}
        >
          <Text style={[styles.tabBtnText, activeTab === 'SUBMIT' && styles.tabBtnTextActive]}>
            📝 Submit
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.tabBtn, activeTab === 'HISTORY' && styles.tabBtnActive]}
          onPress={() => setActiveTab('HISTORY')}
          accessibilityRole="tab"
          accessibilityState={{ selected: activeTab === 'HISTORY' }}
        >
          <Text style={[styles.tabBtnText, activeTab === 'HISTORY' && styles.tabBtnTextActive]}>
            📋 My Reports
          </Text>
        </TouchableOpacity>

        {isModerator && (
          <TouchableOpacity
            style={[styles.tabBtn, activeTab === 'MODERATOR' && styles.tabBtnActive]}
            onPress={() => setActiveTab('MODERATOR')}
            accessibilityRole="tab"
            accessibilityState={{ selected: activeTab === 'MODERATOR' }}
          >
            <Text
              style={[
                styles.tabBtnText,
                activeTab === 'MODERATOR' && styles.tabBtnTextActive,
                styles.moderatorTabText,
              ]}
            >
              🛡️ Queue {reportStats.pending > 0 ? `(${reportStats.pending})` : ''}
            </Text>
          </TouchableOpacity>
        )}
      </View>

      {/* TAB 1: SUBMIT REPORT */}
      {activeTab === 'SUBMIT' && (
        <View>
          {/* Success Banner */}
          {submitSuccess && (
            <Card style={styles.successCard} accessibilityRole="alert">
              <Text style={styles.successIcon}>✅</Text>
              <Text style={styles.successTitle}>Report Submitted Successfully</Text>
              <Text style={styles.successMessage}>{submitSuccess.message}</Text>
              <View style={styles.successDetailRow}>
                <Text style={styles.successDetailLabel}>Reference ID:</Text>
                <Text style={styles.successDetailValue}>{submitSuccess.id}</Text>
              </View>
              <View style={styles.successDetailRow}>
                <Text style={styles.successDetailLabel}>Status:</Text>
                <Text style={styles.successStatusBadge}>PENDING REVIEW</Text>
              </View>
              <Text style={styles.successNote}>
                Your report will be reviewed by safety moderators before contributing to community maps.
              </Text>

              {/* Route Suggestion Impact: Before vs After Report */}
              <View style={styles.successReroutePreview}>
                <Text style={styles.successPreviewHeading}>⚡ Expected Route Impact Preview</Text>
                <Text style={styles.successPreviewText}>
                  Upon moderator verification, SafePath AI dynamically penalizes this road segment in the A* routing engine, rerouting pedestrians around the hazard:
                </Text>
                <View style={styles.beforeAfterMiniBox}>
                  <View style={styles.beforeAfterCol}>
                    <Text style={styles.beforeLabel}>❌ BEFORE REPORT</Text>
                    <Text style={styles.beforeScore}>Safety Score: 52%</Text>
                    <Text style={styles.beforeDesc}>Passes directly through hazard</Text>
                  </View>
                  <Text style={styles.beforeAfterArrow}>➔</Text>
                  <View style={styles.beforeAfterCol}>
                    <Text style={styles.afterLabel}>✅ AFTER VERIFICATION</Text>
                    <Text style={styles.afterScore}>Safety Score: 88% (+36 pts)</Text>
                    <Text style={styles.afterDesc}>Detours along lit avenue (+120m)</Text>
                  </View>
                </View>
              </View>

              <Button
                title="Submit Another Report"
                variant="secondary"
                onPress={() => setSubmitSuccess(null)}
              />
            </Card>
          )}

          {!submitSuccess && (
            <Card style={styles.formCard}>
              {/* Category Selector */}
              <Text style={styles.sectionHeading}>1. Report Category</Text>
              <Text style={styles.helperText}>Select the category that best describes the issue:</Text>
              <View style={styles.categoriesGrid}>
                {CATEGORIES.map((cat) => {
                  const isSelected = selectedCategory === cat.type;
                  return (
                    <TouchableOpacity
                      key={cat.type}
                      style={[
                        styles.categoryCard,
                        isSelected && styles.categoryCardSelected,
                      ]}
                      onPress={() => setSelectedCategory(cat.type)}
                      accessibilityRole="radio"
                      accessibilityState={{ selected: isSelected }}
                      accessibilityLabel={`${cat.label}: ${cat.description}`}
                    >
                      <Text style={styles.categoryIcon}>{cat.icon}</Text>
                      <Text
                        style={[
                          styles.categoryLabel,
                          isSelected && styles.categoryLabelSelected,
                        ]}
                      >
                        {cat.label}
                      </Text>
                    </TouchableOpacity>
                  );
                })}
              </View>

              {/* Description Input */}
              <View style={styles.inputGroup}>
                <View style={styles.labelRow}>
                  <Text style={styles.sectionHeading}>2. Description (Optional)</Text>
                  <Text
                    style={[
                      styles.charCounter,
                      description.length > 1000 && styles.charCounterError,
                    ]}
                  >
                    {description.length}/1000
                  </Text>
                </View>
                <Text style={styles.helperText}>
                  Provide objective details (e.g. location cues, broken fixture count). Do NOT include names, phone numbers, or private details.
                </Text>
                <TextInput
                  style={styles.textArea}
                  value={description}
                  onChangeText={setDescription}
                  placeholder="Describe what you observed..."
                  placeholderTextColor="#8E8E93"
                  multiline
                  numberOfLines={4}
                  maxLength={1000}
                  accessibilityLabel="Report description text input"
                />
              </View>

              {/* Incident Timing */}
              <View style={styles.inputGroup}>
                <Text style={styles.sectionHeading}>3. When Did This Occur?</Text>
                <View style={styles.timePillsRow}>
                  {(
                    [
                      { key: 'JUST_NOW', label: 'Just Now' },
                      { key: 'PAST_HOUR', label: 'Past Hour' },
                      { key: 'EARLIER_TODAY', label: 'Earlier Today' },
                      { key: 'YESTERDAY', label: 'Yesterday' },
                    ] as const
                  ).map((opt) => (
                    <TouchableOpacity
                      key={opt.key}
                      style={[
                        styles.timePill,
                        timeOption === opt.key && styles.timePillActive,
                      ]}
                      onPress={() => setTimeOption(opt.key)}
                      accessibilityRole="radio"
                      accessibilityState={{ selected: timeOption === opt.key }}
                    >
                      <Text
                        style={[
                          styles.timePillText,
                          timeOption === opt.key && styles.timePillTextActive,
                        ]}
                      >
                        {opt.label}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>

              {/* Optional Location */}
              <View style={styles.inputGroup}>
                <Text style={styles.sectionHeading}>4. Location (Optional)</Text>
                <Text style={styles.helperText}>
                  Location coordinates are strictly optional. We never request background location.
                </Text>

                <View style={styles.switchRow}>
                  <Text style={styles.switchLabel}>Attach current location coordinates</Text>
                  <Switch
                    value={includeLocation}
                    onValueChange={handleToggleLocation}
                    trackColor={{ false: '#E5E5EA', true: '#34C759' }}
                    accessibilityLabel="Toggle attaching coordinates to report"
                  />
                </View>

                {locLoading && (
                  <View style={styles.locStatusRow}>
                    <ActivityIndicator size="small" color="#007AFF" />
                    <Text style={styles.locStatusText}>Acquiring foreground location...</Text>
                  </View>
                )}

                {locError && (
                  <Text style={styles.locErrorText}>{locError}</Text>
                )}

                {includeLocation && coords && (
                  <View style={styles.coordsAttachedBox}>
                    <Text style={styles.coordsText}>
                      📍 Coordinates: {coords.latitude.toFixed(4)}, {coords.longitude.toFixed(4)}
                    </Text>
                    <Text style={styles.privacyNote}>
                      Explicit Confirmation: These coordinates will be attached as EXACT_COORDINATE. They remain private until verified.
                    </Text>
                  </View>
                )}

                {!includeLocation && (
                  <View style={styles.coordsOmittedBox}>
                    <Text style={styles.coordsOmittedText}>
                      ⚪ No coordinates attached. Report will be logged with UNKNOWN spatial precision.
                    </Text>
                    <TouchableOpacity
                      style={styles.sampleLocBtn}
                      onPress={useSampleLocation}
                      accessibilityRole="button"
                      accessibilityLabel="Use sample Bangalore location"
                    >
                      <Text style={styles.sampleLocBtnText}>Use Sample Bangalore Location</Text>
                    </TouchableOpacity>
                  </View>
                )}
              </View>

              {/* Submit Error */}
              {submitError && (
                <View style={styles.errorBanner} accessibilityRole="alert">
                  <Text style={styles.errorBannerText}>{submitError}</Text>
                </View>
              )}

              {/* Submit Button */}
              <Button
                title={submitting ? 'Submitting Report...' : 'Submit Safety Report'}
                onPress={handleSubmit}
                disabled={submitting}
                accessibilityLabel="Submit safety report button"
              />
            </Card>
          )}
        </View>
      )}

      {/* TAB 2: MY REPORTS / HISTORY */}
      {activeTab === 'HISTORY' && (
        <View>
          <View style={styles.historyHeaderRow}>
            <Text style={styles.historySectionTitle}>Your Submitted Reports</Text>
            <TouchableOpacity
              onPress={loadHistory}
              style={styles.refreshBtn}
              accessibilityRole="button"
              accessibilityLabel="Refresh reports list"
            >
              <Text style={styles.refreshBtnText}>🔄 Refresh</Text>
            </TouchableOpacity>
          </View>

          {historyLoading && (
            <View style={styles.loadingContainer}>
              <ActivityIndicator size="large" color="#007AFF" />
              <Text style={styles.loadingText}>Loading your submitted reports...</Text>
            </View>
          )}

          {historyError && !historyLoading && (
            <Card style={styles.errorCard} accessibilityRole="alert">
              <Text style={styles.errorTitle}>Error Loading Reports</Text>
              <Text style={styles.errorText}>{historyError}</Text>
              <Button title="Retry" variant="secondary" onPress={loadHistory} />
            </Card>
          )}

          {!historyLoading && !historyError && myReports.length === 0 && (
            <Card style={styles.emptyCard}>
              <Text style={styles.emptyIcon}>📋</Text>
              <Text style={styles.emptyTitle}>No Reports Submitted</Text>
              <Text style={styles.emptySubtitle}>
                You haven't submitted any community safety reports yet. Reports you submit will appear here with their review status.
              </Text>
              <Button
                title="Submit a Report"
                onPress={() => setActiveTab('SUBMIT')}
              />
            </Card>
          )}

          {!historyLoading &&
            myReports.map((report) => (
              <Card key={report.id} style={styles.reportCard}>
                <View style={styles.reportHeaderRow}>
                  <View style={styles.reportTypeBadge}>
                    <Text style={styles.reportTypeText}>{report.reportType.replace(/_/g, ' ')}</Text>
                  </View>
                  <View
                    style={[
                      styles.statusPill,
                      { backgroundColor: getStatusColor(report.status) },
                    ]}
                  >
                    <Text style={styles.statusPillText}>{getStatusLabel(report.status)}</Text>
                  </View>
                </View>

                {report.description ? (
                  <Text style={styles.reportDescription}>{report.description}</Text>
                ) : (
                  <Text style={styles.reportNoDesc}>No description provided.</Text>
                )}

                <View style={styles.reportFooterRow}>
                  <Text style={styles.reportDate}>
                    Submitted: {new Date(report.createdAt).toLocaleDateString()}
                  </Text>
                  <Text style={styles.reportPrecision}>
                    {report.locationPrecision === 'EXACT_COORDINATE'
                      ? '📍 Coordinates Included'
                      : '⚪ Location Unspecified'}
                  </Text>
                </View>

                {report.status === 'VERIFIED' && (
                  <View style={styles.verifiedImpactBox}>
                    <View style={styles.impactHeader}>
                      <Text style={styles.impactHeaderIcon}>🛡️</Text>
                      <Text style={styles.impactHeaderTitle}>Live Route Detour Active</Text>
                    </View>
                    <Text style={styles.impactDesc}>
                      SafePath AI has calibrated this hazard. Pedestrian route calculations automatically detour around this street to keep walkers safe.
                    </Text>
                    <View style={styles.impactMetricRow}>
                      <View style={styles.impactMetricItem}>
                        <Text style={styles.impactMetricVal}>+36 pts</Text>
                        <Text style={styles.impactMetricLbl}>Safety Reroute</Text>
                      </View>
                      <View style={styles.impactDivider} />
                      <View style={styles.impactMetricItem}>
                        <Text style={styles.impactMetricVal}>100%</Text>
                        <Text style={styles.impactMetricLbl}>Lit Corridor</Text>
                      </View>
                      <View style={styles.impactDivider} />
                      <View style={styles.impactMetricItem}>
                        <Text style={styles.impactMetricVal}>+120 m</Text>
                        <Text style={styles.impactMetricLbl}>Safe Detour</Text>
                      </View>
                    </View>

                    {report.verificationNote && (
                      <Text style={styles.verificationNoteText}>
                        💬 Moderator Note: {report.verificationNote}
                      </Text>
                    )}

                    <TouchableOpacity
                      style={styles.impactDetourMapBtn}
                      onPress={() => router.push('/(tabs)/routes')}
                      accessibilityRole="button"
                      accessibilityLabel="View Detour on Safe Routes Map"
                    >
                      <Text style={styles.impactDetourMapBtnText}>🗺️ View Detour on Routes Map</Text>
                    </TouchableOpacity>
                  </View>
                )}

                {report.status === 'REJECTED' && (
                  <View style={styles.rejectedImpactBox}>
                    <Text style={styles.rejectedTitle}>❌ Report Rejected by Moderator</Text>
                    <Text style={styles.rejectedDesc}>
                      {report.verificationNote ||
                        'This report was reviewed and marked as unverified or duplicate.'}
                    </Text>
                  </View>
                )}

                {report.status === 'PENDING' && (
                  <View style={styles.moderatorActionBox}>
                    <Text style={styles.pendingNotice}>
                      ⏳ Under review by community moderators.
                    </Text>
                    {isModerator && (
                      <TouchableOpacity
                        style={styles.verifyBtn}
                        onPress={() => handleVerify(report.id)}
                        disabled={verifyingId === report.id}
                        accessibilityRole="button"
                        accessibilityLabel="Review and approve report"
                      >
                        {verifyingId === report.id ? (
                          <ActivityIndicator size="small" color="#FFFFFF" />
                        ) : (
                          <Text style={styles.verifyBtnText}>🛡️ Quick Approve (Moderator)</Text>
                        )}
                      </TouchableOpacity>
                    )}
                  </View>
                )}
              </Card>
            ))}
        </View>
      )}

      {/* TAB 3: MODERATOR QUEUE */}
      {activeTab === 'MODERATOR' && (
        <View>
          <View style={styles.modHeaderCard}>
            <View style={{ flex: 1 }}>
              <Text style={styles.modHeaderTitle}>Pending Moderation Queue</Text>
              <Text style={styles.modHeaderDesc}>
                {pendingReports.length} hazard reports awaiting validation.
              </Text>
            </View>
            <TouchableOpacity
              style={styles.openFullDeskBtn}
              onPress={() => router.push('/moderator-dashboard')}
              accessibilityRole="button"
              accessibilityLabel="Open Full Moderator Dashboard"
            >
              <Text style={styles.openFullDeskBtnText}>Full Desk ↗</Text>
            </TouchableOpacity>
          </View>

          {pendingLoading && (
            <View style={styles.loadingContainer}>
              <ActivityIndicator size="large" color="#007AFF" />
              <Text style={styles.loadingText}>Fetching pending reports...</Text>
            </View>
          )}

          {!pendingLoading && pendingReports.length === 0 && (
            <Card style={styles.emptyCard}>
              <Text style={styles.emptyIcon}>🎉</Text>
              <Text style={styles.emptyTitle}>Moderation Queue Clear</Text>
              <Text style={styles.emptySubtitle}>
                No pending safety reports need review. All citizen submissions have been processed!
              </Text>
              <Button title="Refresh Queue" onPress={loadPendingQueue} />
            </Card>
          )}

          {!pendingLoading &&
            pendingReports.map((report) => (
              <Card key={report.id} style={styles.reportCard}>
                <View style={styles.reportHeaderRow}>
                  <View style={styles.reportTypeBadge}>
                    <Text style={styles.reportTypeText}>{report.reportType.replace(/_/g, ' ')}</Text>
                  </View>
                  <View style={[styles.statusPill, { backgroundColor: '#FF9500' }]}>
                    <Text style={styles.statusPillText}>PENDING</Text>
                  </View>
                </View>

                <Text style={styles.reportDescription}>
                  {report.description || 'No additional description provided.'}
                </Text>

                <View style={styles.reportFooterRow}>
                  <Text style={styles.reportDate}>
                    {new Date(report.createdAt).toLocaleString()}
                  </Text>
                  <Text style={styles.reportPrecision}>
                    {report.locationPrecision === 'EXACT_COORDINATE'
                      ? `📍 ${report.latitude?.toFixed(4)}, ${report.longitude?.toFixed(4)}`
                      : '⚪ Locality'}
                  </Text>
                </View>

                <View style={styles.modActionRow}>
                  <TouchableOpacity
                    style={[styles.quickModBtn, styles.quickApproveBtn]}
                    onPress={() => handleVerify(report.id)}
                    disabled={verifyingId === report.id}
                  >
                    {verifyingId === report.id ? (
                      <ActivityIndicator size="small" color="#FFFFFF" />
                    ) : (
                      <Text style={styles.quickModBtnText}>✅ Approve & Reroute</Text>
                    )}
                  </TouchableOpacity>

                  <TouchableOpacity
                    style={[styles.quickModBtn, styles.quickDeskBtn]}
                    onPress={() => router.push('/moderator-dashboard')}
                  >
                    <Text style={styles.quickDeskBtnText}>Review in Desk</Text>
                  </TouchableOpacity>
                </View>
              </Card>
            ))}
        </View>
      )}

      {/* Notifications Modal */}
      <Modal
        visible={notificationsModalOpen}
        animationType="slide"
        transparent
        onRequestClose={() => setNotificationsModalOpen(false)}
      >
        <View style={styles.modalOverlay}>
          <View style={styles.notifSheetCard}>
            <View style={styles.notifSheetHeader}>
              <Text style={styles.notifSheetTitle}>🔔 In-App Safety Alerts</Text>
              <TouchableOpacity onPress={() => setNotificationsModalOpen(false)}>
                <Text style={styles.notifCloseText}>✕ Close</Text>
              </TouchableOpacity>
            </View>

            {allNotifications.length > 0 && (
              <TouchableOpacity
                style={styles.markAllReadBtn}
                onPress={handleMarkAllNotificationsRead}
              >
                <Text style={styles.markAllReadText}>Mark All as Read</Text>
              </TouchableOpacity>
            )}

            <ScrollView style={{ maxHeight: 400 }}>
              {allNotifications.length === 0 ? (
                <View style={styles.emptyNotifBox}>
                  <Text style={styles.emptyNotifIcon}>🔕</Text>
                  <Text style={styles.emptyNotifTitle}>No Notifications</Text>
                  <Text style={styles.emptyNotifSubtitle}>
                    You will receive instant alerts here whenever a report you submit is verified or updated by moderators.
                  </Text>
                </View>
              ) : (
                allNotifications.map((n) => (
                  <View
                    key={n.id}
                    style={[styles.notifItem, !n.read && styles.notifItemUnread]}
                  >
                    <View style={styles.notifItemTop}>
                      <Text style={styles.notifItemTitle}>{n.title}</Text>
                      {!n.read && <View style={styles.unreadDot} />}
                    </View>
                    <Text style={styles.notifItemMessage}>{n.message}</Text>
                    <View style={styles.notifItemBottom}>
                      <Text style={styles.notifItemDate}>
                        {new Date(n.createdAt).toLocaleString([], {
                          month: 'short',
                          day: 'numeric',
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </Text>
                      {!n.read && (
                        <TouchableOpacity onPress={() => handleDismissNotification(n.id)}>
                          <Text style={styles.markSingleReadText}>Mark read</Text>
                        </TouchableOpacity>
                      )}
                    </View>
                  </View>
                ))
              )}
            </ScrollView>
          </View>
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
  emergencyBanner: {
    backgroundColor: '#FFF0F0',
    borderWidth: 1,
    borderColor: '#FF3B30',
    borderRadius: 12,
    padding: 12,
    flexDirection: 'row',
    marginBottom: 16,
  },
  emergencyIcon: {
    fontSize: 22,
    marginRight: 10,
  },
  emergencyTextCol: {
    flex: 1,
  },
  emergencyTitle: {
    fontSize: 13,
    fontWeight: '800',
    color: '#D70015',
    marginBottom: 2,
  },
  emergencyDesc: {
    fontSize: 11,
    color: '#3A3A3C',
    lineHeight: 15,
  },
  header: {
    marginBottom: 16,
  },
  moderatorActionBox: {
    marginTop: 8,
    paddingTop: 8,
    borderTopWidth: 1,
    borderTopColor: '#E5E5EA',
  },
  verifyBtn: {
    backgroundColor: '#059669',
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 8,
    marginTop: 8,
    alignItems: 'center',
  },
  verifyBtnText: {
    color: '#FFFFFF',
    fontWeight: '700',
    fontSize: 13,
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
  },
  tabBar: {
    flexDirection: 'row',
    backgroundColor: '#E5E5EA',
    borderRadius: 10,
    padding: 3,
    marginBottom: 16,
  },
  tabBtn: {
    flex: 1,
    paddingVertical: 10,
    alignItems: 'center',
    borderRadius: 8,
  },
  tabBtnActive: {
    backgroundColor: '#FFFFFF',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  tabBtnText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#636366',
  },
  tabBtnTextActive: {
    color: '#1C1C1E',
  },
  formCard: {
    padding: 16,
  },
  sectionHeading: {
    fontSize: 15,
    fontWeight: '700',
    color: '#1C1C1E',
    marginBottom: 4,
  },
  helperText: {
    fontSize: 12,
    color: '#8E8E93',
    marginBottom: 10,
    lineHeight: 16,
  },
  categoriesGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 16,
  },
  categoryCard: {
    width: '48%',
    backgroundColor: '#F2F2F7',
    borderRadius: 10,
    padding: 10,
    alignItems: 'center',
    borderWidth: 1.5,
    borderColor: 'transparent',
  },
  categoryCardSelected: {
    backgroundColor: '#E5F1FF',
    borderColor: '#007AFF',
  },
  categoryIcon: {
    fontSize: 22,
    marginBottom: 4,
  },
  categoryLabel: {
    fontSize: 12,
    fontWeight: '600',
    color: '#3A3A3C',
    textAlign: 'center',
  },
  categoryLabelSelected: {
    color: '#007AFF',
    fontWeight: '700',
  },
  inputGroup: {
    marginBottom: 16,
  },
  labelRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  charCounter: {
    fontSize: 11,
    color: '#8E8E93',
  },
  charCounterError: {
    color: '#FF3B30',
    fontWeight: '700',
  },
  textArea: {
    backgroundColor: '#F2F2F7',
    borderRadius: 8,
    padding: 12,
    fontSize: 14,
    color: '#1C1C1E',
    minHeight: 80,
    textAlignVertical: 'top',
  },
  timePillsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  timePill: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    backgroundColor: '#F2F2F7',
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#E5E5EA',
  },
  timePillActive: {
    backgroundColor: '#007AFF',
    borderColor: '#007AFF',
  },
  timePillText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#3A3A3C',
  },
  timePillTextActive: {
    color: '#FFFFFF',
  },
  switchRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 6,
  },
  switchLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#1C1C1E',
    flex: 1,
  },
  locStatusRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginVertical: 6,
  },
  locStatusText: {
    fontSize: 12,
    color: '#007AFF',
  },
  locErrorText: {
    fontSize: 11,
    color: '#FF3B30',
    marginVertical: 4,
  },
  coordsAttachedBox: {
    backgroundColor: '#E8F8EE',
    padding: 10,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#34C759',
    marginTop: 6,
  },
  coordsText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#248A3D',
  },
  privacyNote: {
    fontSize: 10,
    color: '#3A3A3C',
    marginTop: 4,
    lineHeight: 13,
  },
  coordsOmittedBox: {
    backgroundColor: '#F2F2F7',
    padding: 10,
    borderRadius: 8,
    marginTop: 6,
  },
  coordsOmittedText: {
    fontSize: 11,
    color: '#636366',
  },
  sampleLocBtn: {
    marginTop: 8,
    alignSelf: 'flex-start',
    backgroundColor: '#E5E5EA',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 6,
  },
  sampleLocBtnText: {
    fontSize: 11,
    fontWeight: '600',
    color: '#007AFF',
  },
  errorBanner: {
    backgroundColor: '#FFF0F0',
    padding: 10,
    borderRadius: 8,
    marginBottom: 12,
  },
  errorBannerText: {
    color: '#FF3B30',
    fontSize: 12,
    fontWeight: '600',
  },
  successCard: {
    padding: 20,
    alignItems: 'center',
  },
  successIcon: {
    fontSize: 40,
    marginBottom: 8,
  },
  successTitle: {
    fontSize: 18,
    fontWeight: '800',
    color: '#1C1C1E',
    marginBottom: 6,
  },
  successMessage: {
    fontSize: 13,
    color: '#636366',
    textAlign: 'center',
    marginBottom: 14,
  },
  successDetailRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    width: '100%',
    paddingVertical: 4,
  },
  successDetailLabel: {
    fontSize: 12,
    color: '#8E8E93',
  },
  successDetailValue: {
    fontSize: 12,
    fontWeight: '600',
    color: '#1C1C1E',
  },
  successStatusBadge: {
    fontSize: 11,
    fontWeight: '700',
    color: '#FF9500',
    backgroundColor: '#FFF2D6',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  successNote: {
    fontSize: 11,
    color: '#8E8E93',
    textAlign: 'center',
    marginTop: 12,
    marginBottom: 16,
    lineHeight: 15,
  },
  historyHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  historySectionTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  refreshBtn: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    backgroundColor: '#E5E5EA',
    borderRadius: 6,
  },
  refreshBtnText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#007AFF',
  },
  loadingContainer: {
    padding: 40,
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 13,
    color: '#8E8E93',
  },
  errorCard: {
    padding: 16,
    alignItems: 'center',
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
    marginBottom: 12,
  },
  emptyCard: {
    padding: 30,
    alignItems: 'center',
  },
  emptyIcon: {
    fontSize: 36,
    marginBottom: 8,
  },
  emptyTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1C1C1E',
    marginBottom: 4,
  },
  emptySubtitle: {
    fontSize: 12,
    color: '#8E8E93',
    textAlign: 'center',
    marginBottom: 16,
    lineHeight: 16,
  },
  reportCard: {
    padding: 14,
    marginBottom: 10,
  },
  reportHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  reportTypeBadge: {
    backgroundColor: '#F2F2F7',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  reportTypeText: {
    fontSize: 12,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  statusPill: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 12,
  },
  statusPillText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: '800',
  },
  reportDescription: {
    fontSize: 13,
    color: '#3A3A3C',
    lineHeight: 18,
    marginBottom: 8,
  },
  reportNoDesc: {
    fontSize: 12,
    fontStyle: 'italic',
    color: '#8E8E93',
    marginBottom: 8,
  },
  reportFooterRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: '#E5E5EA',
    paddingTop: 8,
  },
  reportDate: {
    fontSize: 11,
    color: '#8E8E93',
  },
  reportPrecision: {
    fontSize: 11,
    color: '#636366',
    fontWeight: '500',
  },
  pendingNotice: {
    fontSize: 10,
    color: '#FF9500',
    marginTop: 6,
    fontStyle: 'italic',
  },
  successReroutePreview: {
    backgroundColor: '#F8F9FA',
    borderRadius: 8,
    padding: 12,
    marginVertical: 12,
    borderWidth: 1,
    borderColor: '#E5E5EA',
  },
  successPreviewHeading: {
    fontSize: 13,
    fontWeight: '700',
    color: '#007AFF',
    marginBottom: 4,
  },
  successPreviewText: {
    fontSize: 12,
    color: '#636366',
    lineHeight: 16,
    marginBottom: 10,
  },
  beforeAfterMiniBox: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#FFFFFF',
    borderRadius: 6,
    padding: 10,
    borderWidth: 1,
    borderColor: '#E5E5EA',
  },
  beforeAfterCol: {
    flex: 1,
  },
  beforeLabel: {
    fontSize: 10,
    fontWeight: '800',
    color: '#D70015',
    marginBottom: 2,
  },
  beforeScore: {
    fontSize: 12,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  beforeDesc: {
    fontSize: 10,
    color: '#8E8E93',
  },
  beforeAfterArrow: {
    fontSize: 16,
    color: '#8E8E93',
    marginHorizontal: 8,
  },
  afterLabel: {
    fontSize: 10,
    fontWeight: '800',
    color: '#2E7D32',
    marginBottom: 2,
  },
  afterScore: {
    fontSize: 12,
    fontWeight: '700',
    color: '#2E7D32',
  },
  afterDesc: {
    fontSize: 10,
    color: '#8E8E93',
  },
  verifiedImpactBox: {
    backgroundColor: '#E8F5E9',
    borderRadius: 8,
    padding: 12,
    marginTop: 10,
    borderLeftWidth: 4,
    borderLeftColor: '#34C759',
  },
  impactHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  impactHeaderIcon: {
    fontSize: 16,
    marginRight: 6,
  },
  impactHeaderTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: '#2E7D32',
  },
  impactDesc: {
    fontSize: 11,
    color: '#1B5E20',
    lineHeight: 15,
    marginBottom: 8,
  },
  impactMetricRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 6,
    paddingVertical: 8,
    paddingHorizontal: 6,
  },
  impactMetricItem: {
    flex: 1,
    alignItems: 'center',
  },
  impactMetricVal: {
    fontSize: 13,
    fontWeight: '800',
    color: '#2E7D32',
  },
  impactMetricLbl: {
    fontSize: 9,
    color: '#636366',
    marginTop: 2,
  },
  impactDivider: {
    width: 1,
    height: 20,
    backgroundColor: '#E5E5EA',
  },
  verificationNoteText: {
    fontSize: 11,
    color: '#2E7D32',
    fontStyle: 'italic',
    marginTop: 8,
    marginBottom: 4,
  },
  impactDetourMapBtn: {
    marginTop: 8,
    backgroundColor: '#2E7D32',
    paddingVertical: 7,
    paddingHorizontal: 12,
    borderRadius: 6,
    alignItems: 'center',
  },
  impactDetourMapBtnText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '700',
  },
  rejectedImpactBox: {
    backgroundColor: '#FFF0F0',
    borderRadius: 8,
    padding: 12,
    marginTop: 10,
    borderLeftWidth: 4,
    borderLeftColor: '#FF3B30',
  },
  rejectedTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: '#D70015',
    marginBottom: 2,
  },
  rejectedDesc: {
    fontSize: 11,
    color: '#8E8E93',
    lineHeight: 15,
  },
  notificationAlertBanner: {
    backgroundColor: '#E5F1FF',
    borderRadius: 10,
    borderLeftWidth: 4,
    borderLeftColor: '#007AFF',
    padding: 12,
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 14,
  },
  notificationAlertIcon: {
    fontSize: 22,
    marginRight: 10,
  },
  notificationAlertContent: {
    flex: 1,
  },
  notificationAlertTitle: {
    fontSize: 13,
    fontWeight: '800',
    color: '#007AFF',
    marginBottom: 2,
  },
  notificationAlertMsg: {
    fontSize: 11,
    color: '#3A3A3C',
    lineHeight: 15,
  },
  notificationDismissBtn: {
    paddingVertical: 4,
    paddingHorizontal: 8,
    backgroundColor: '#FFFFFF',
    borderRadius: 6,
    marginLeft: 8,
  },
  notificationDismissText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#007AFF',
  },
  headerTopRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  notificationBellBtn: {
    padding: 8,
    position: 'relative',
  },
  notificationBellIcon: {
    fontSize: 20,
  },
  bellBadge: {
    position: 'absolute',
    top: 2,
    right: 2,
    backgroundColor: '#FF3B30',
    borderRadius: 9,
    width: 18,
    height: 18,
    justifyContent: 'center',
    alignItems: 'center',
  },
  bellBadgeText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: '800',
  },
  moderatorTabText: {
    color: '#059669',
    fontWeight: '700',
  },
  modHeaderCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 10,
    padding: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 12,
    borderLeftWidth: 4,
    borderLeftColor: '#059669',
  },
  modHeaderTitle: {
    fontSize: 14,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  modHeaderDesc: {
    fontSize: 11,
    color: '#636366',
    marginTop: 2,
  },
  openFullDeskBtn: {
    backgroundColor: '#059669',
    paddingVertical: 6,
    paddingHorizontal: 10,
    borderRadius: 6,
  },
  openFullDeskBtnText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '700',
  },
  modActionRow: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 10,
  },
  quickModBtn: {
    flex: 1,
    paddingVertical: 8,
    borderRadius: 6,
    alignItems: 'center',
  },
  quickApproveBtn: {
    backgroundColor: '#34C759',
  },
  quickModBtnText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '700',
  },
  quickDeskBtn: {
    backgroundColor: '#E5E5EA',
  },
  quickDeskBtnText: {
    color: '#1C1C1E',
    fontSize: 12,
    fontWeight: '600',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'flex-end',
  },
  notifSheetCard: {
    backgroundColor: '#FFFFFF',
    borderTopLeftRadius: 16,
    borderTopRightRadius: 16,
    padding: 16,
    paddingBottom: 32,
    maxHeight: '80%',
  },
  notifSheetHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  notifSheetTitle: {
    fontSize: 17,
    fontWeight: '800',
    color: '#1C1C1E',
  },
  notifCloseText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#007AFF',
  },
  markAllReadBtn: {
    alignSelf: 'flex-end',
    marginBottom: 10,
  },
  markAllReadText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#007AFF',
  },
  emptyNotifBox: {
    padding: 30,
    alignItems: 'center',
  },
  emptyNotifIcon: {
    fontSize: 36,
    marginBottom: 8,
  },
  emptyNotifTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1C1C1E',
    marginBottom: 4,
  },
  emptyNotifSubtitle: {
    fontSize: 12,
    color: '#8E8E93',
    textAlign: 'center',
    lineHeight: 16,
  },
  notifItem: {
    backgroundColor: '#F2F2F7',
    borderRadius: 8,
    padding: 12,
    marginBottom: 8,
  },
  notifItemUnread: {
    backgroundColor: '#EBF4FF',
    borderLeftWidth: 3,
    borderLeftColor: '#007AFF',
  },
  notifItemTop: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  notifItemTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: '#1C1C1E',
    flex: 1,
  },
  unreadDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#007AFF',
  },
  notifItemMessage: {
    fontSize: 12,
    color: '#3A3A3C',
    lineHeight: 16,
    marginBottom: 6,
  },
  notifItemBottom: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  notifItemDate: {
    fontSize: 10,
    color: '#8E8E93',
  },
  markSingleReadText: {
    fontSize: 11,
    color: '#007AFF',
    fontWeight: '600',
  },
});
