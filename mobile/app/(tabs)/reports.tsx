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
} from 'react-native';
import * as Location from 'expo-location';
import { Card } from '../../components/Card';
import { Button } from '../../components/Button';
import {
  submitReport,
  getMyReports,
  verifyReport,
  ReportType,
  ReportStatus,
  CommunityReportResponse,
  SubmitReportResponse,
} from '../../services/reports';

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
  // Navigation / active tab
  const [activeTab, setActiveTab] = useState<'SUBMIT' | 'HISTORY'>('SUBMIT');

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
    if (activeTab === 'HISTORY') {
      loadHistory();
    }
  }, [activeTab, loadHistory]);

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

      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.title} accessibilityRole="header">
          Community Safety Reports
        </Text>
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
            📝 Submit Report
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

                {report.status === 'PENDING' && (
                  <View style={styles.moderatorActionBox}>
                    <Text style={styles.pendingNotice}>
                      ⏳ Under review by community moderators.
                    </Text>
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
                        <Text style={styles.verifyBtnText}>🛡️ Review & Approve (Moderator)</Text>
                      )}
                    </TouchableOpacity>
                  </View>
                )}
              </Card>
            ))}
        </View>
      )}
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
});
