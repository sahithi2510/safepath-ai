import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  Linking,
  Platform,
} from 'react-native';
import { useRouter } from 'expo-router';
import * as Location from 'expo-location';
import {
  EmergencyService,
  EmergencyContact,
  OFFICIAL_HELPLINES,
  HelplineInfo,
} from '../services/emergency';
import { Input } from '../components/Input';
import { Button } from '../components/Button';
import { Card } from '../components/Card';

export default function EmergencyScreen() {
  const router = useRouter();

  // Contacts state
  const [contacts, setContacts] = useState<EmergencyContact[]>([]);
  const [loadingContacts, setLoadingContacts] = useState(true);

  // Add contact form state
  const [showAddForm, setShowAddForm] = useState(false);
  const [contactName, setContactName] = useState('');
  const [contactPhone, setContactPhone] = useState('');
  const [contactRelationship, setContactRelationship] = useState('');
  const [contactPrimary, setContactPrimary] = useState(false);
  const [savingContact, setSavingContact] = useState(false);
  const [nameError, setNameError] = useState<string | undefined>();
  const [phoneError, setPhoneError] = useState<string | undefined>();

  // SOS state
  const [triggeringSos, setTriggeringSos] = useState(false);
  const [lastSosEvent, setLastSosEvent] = useState<{
    eventId?: string;
    loggedAt: string;
    message: string;
    coords?: { latitude: number; longitude: number };
  } | null>(null);

  // General banners
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const loadContacts = useCallback(async () => {
    try {
      setLoadingContacts(true);
      const data = await EmergencyService.getContacts();
      setContacts(data);
      if (data.length === 0) {
        setContactPrimary(true);
      }
    } catch (err: any) {
      const msg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        'Could not load emergency contacts from server.';
      setErrorMessage(msg);
    } finally {
      setLoadingContacts(false);
    }
  }, []);

  useEffect(() => {
    loadContacts();
  }, [loadContacts]);

  // Safe phone dialer
  const handleCall = async (phoneNumber: string, label: string) => {
    const cleanNumber = phoneNumber.replace(/[^0-9+]/g, '');
    const url = `tel:${cleanNumber}`;

    Alert.alert(
      `Call ${label}`,
      `Do you want to place a phone call to ${label} (${phoneNumber})?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Call Now',
          style: 'default',
          onPress: async () => {
            try {
              // Direct invocation bypasses Android 11+ package visibility query filter
              await Linking.openURL(url);
            } catch (err: any) {
              Alert.alert(
                'Dialer Notice',
                `Could not launch phone dialer automatically. Please manually call ${phoneNumber}.`
              );
            }
          },
        },
      ]
    );
  };

  // Safe SMS sender
  const handleSendSms = async (contact: EmergencyContact) => {
    let locStr = '';
    if (lastSosEvent?.coords) {
      locStr = ` My current location is https://maps.google.com/?q=${lastSosEvent.coords.latitude},${lastSosEvent.coords.longitude}`;
    }

    const message = `EMERGENCY: I need help.${locStr} (Sent via SafePath AI)`;
    const separator = Platform.OS === 'ios' ? '&' : '?';
    const cleanNumber = contact.phoneNumber.replace(/[^0-9+]/g, '');
    const url = `sms:${cleanNumber}${separator}body=${encodeURIComponent(message)}`;

    try {
      await Linking.openURL(url);
    } catch (e) {
      Alert.alert(
        'SMS Notice',
        `Could not open SMS app directly. Please manually message ${contact.name} at ${contact.phoneNumber}.`
      );
    }
  };

  // Trigger SOS Workflow
  const handleTriggerSos = () => {
    Alert.alert(
      '🚨 Confirm SOS Emergency Alert',
      'This will record an official emergency SOS audit event with your current location coordinates.\n\nIMPORTANT: SafePath AI does NOT directly dispatch first responders. You must call 112 or emergency services directly.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Record SOS Alert',
          style: 'destructive',
          onPress: executeSos,
        },
      ]
    );
  };

  const executeSos = async () => {
    setTriggeringSos(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    let coords: { latitude: number; longitude: number } | undefined = undefined;

    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status === 'granted') {
        const location = await Location.getCurrentPositionAsync({
          accuracy: Location.Accuracy.Balanced,
        });
        coords = {
          latitude: location.coords.latitude,
          longitude: location.coords.longitude,
        };
      }
    } catch (locErr) {
      // Gracefully continue without coordinates if GPS unavailable
    }

    try {
      const response = await EmergencyService.triggerSos({
        latitude: coords?.latitude,
        longitude: coords?.longitude,
        note: 'Mobile app SOS button triggered',
      });

      setLastSosEvent({
        eventId: response.eventId,
        loggedAt: response.loggedAt,
        message: response.message,
        coords,
      });

      setSuccessMessage('SOS event recorded on server. Please call emergency services below.');
    } catch (apiErr: any) {
      const msg =
        apiErr.response?.data?.message ||
        apiErr.response?.data?.error ||
        apiErr.message ||
        'Could not log SOS to server (network error). Please call emergency services directly!';
      setErrorMessage(msg);

      // Still record a local emergency state so user can immediately call/SMS
      setLastSosEvent({
        loggedAt: new Date().toISOString(),
        message: 'Local emergency triggered (server offline). Call emergency numbers below.',
        coords,
      });
    } finally {
      setTriggeringSos(false);
    }
  };

  // Validate contact form
  const validateContactForm = (): boolean => {
    let isValid = true;
    setNameError(undefined);
    setPhoneError(undefined);

    const trimmedName = contactName.trim();
    if (!trimmedName) {
      setNameError('Contact name is required.');
      isValid = false;
    } else if (trimmedName.length > 255) {
      setNameError('Name cannot exceed 255 characters.');
      isValid = false;
    }

    const trimmedPhone = contactPhone.trim();
    if (!trimmedPhone) {
      setPhoneError('Phone number is required.');
      isValid = false;
    } else {
      const phoneRegex = /^[+]?[(]?[0-9]{1,4}[)]?[-\s./0-9]{5,20}$/;
      if (!phoneRegex.test(trimmedPhone)) {
        setPhoneError('Please enter a valid phone number (e.g. +91 9876543210).');
        isValid = false;
      }
    }

    return isValid;
  };

  // Save contact
  const handleSaveContact = async () => {
    if (!validateContactForm()) return;

    setSavingContact(true);
    setErrorMessage(null);
    try {
      await EmergencyService.addContact({
        name: contactName.trim(),
        phoneNumber: contactPhone.trim(),
        relationship: contactRelationship.trim() || undefined,
        primary: contactPrimary,
      });

      setContactName('');
      setContactPhone('');
      setContactRelationship('');
      setContactPrimary(false);
      setShowAddForm(false);
      setSuccessMessage('Emergency contact saved successfully.');
      await loadContacts();
    } catch (err: any) {
      const msg =
        err.response?.data?.message ||
        err.response?.data?.error ||
        err.message ||
        'Failed to save emergency contact.';
      setErrorMessage(msg);
    } finally {
      setSavingContact(false);
    }
  };

  // Delete contact
  const handleDeleteContact = (contact: EmergencyContact) => {
    Alert.alert(
      'Delete Contact',
      `Are you sure you want to remove ${contact.name} from your emergency contacts?`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            try {
              await EmergencyService.deleteContact(contact.id);
              setSuccessMessage(`Removed ${contact.name}.`);
              await loadContacts();
            } catch (err: any) {
              const msg =
                err.response?.data?.message ||
                err.response?.data?.error ||
                'Failed to delete contact.';
              setErrorMessage(msg);
            }
          },
        },
      ]
    );
  };

  return (
    <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
      {/* Top Header */}
      <View style={styles.headerRow}>
        <TouchableOpacity
          style={styles.backButton}
          onPress={() => router.back()}
          accessibilityLabel="Back"
          accessibilityRole="button"
        >
          <Text style={styles.backButtonText}>‹ Back</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Emergency & SOS</Text>
        <View style={{ width: 60 }} />
      </View>

      {/* Notifications / Alerts */}
      {errorMessage && (
        <View style={styles.errorBanner} accessibilityRole="alert">
          <Text style={styles.errorBannerText}>{errorMessage}</Text>
        </View>
      )}

      {successMessage && (
        <View style={styles.successBanner} accessibilityRole="alert">
          <Text style={styles.successBannerText}>{successMessage}</Text>
        </View>
      )}

      {/* SOS Active Banner */}
      {lastSosEvent && (
        <View style={styles.sosActiveCard} accessibilityRole="alert">
          <View style={styles.sosActiveHeader}>
            <Text style={styles.sosActiveIcon}>🚨</Text>
            <View style={{ flex: 1 }}>
              <Text style={styles.sosActiveTitle}>SOS ALERT RECORDED</Text>
              <Text style={styles.sosActiveTime}>
                Logged at: {new Date(lastSosEvent.loggedAt).toLocaleTimeString()}
              </Text>
              {lastSosEvent.eventId && (
                <Text style={styles.sosActiveRef}>
                  Audit Event ID: #{lastSosEvent.eventId.substring(0, 8)}
                </Text>
              )}
              {lastSosEvent.coords && (
                <Text style={styles.sosActiveCoords}>
                  Coordinates: {lastSosEvent.coords.latitude.toFixed(4)},{' '}
                  {lastSosEvent.coords.longitude.toFixed(4)}
                </Text>
              )}
            </View>
          </View>
          <Text style={styles.sosActiveAdvice}>
            Please place a direct phone call to 112 or your primary contact immediately.
          </Text>
        </View>
      )}

      {/* Prominent SOS Trigger Card */}
      <Card style={styles.sosCard}>
        <Text style={styles.sosCardTitle}>Emergency SOS Trigger</Text>

        <TouchableOpacity
          style={[styles.sosButton, triggeringSos && styles.sosButtonDisabled]}
          onPress={handleTriggerSos}
          disabled={triggeringSos}
          activeOpacity={0.8}
          accessibilityLabel="Trigger SOS Alert"
          accessibilityRole="button"
        >
          {triggeringSos ? (
            <ActivityIndicator size="large" color="#FFFFFF" />
          ) : (
            <>
              <Text style={styles.sosButtonIcon}>🚨</Text>
              <Text style={styles.sosButtonText}>TRIGGER SOS ALERT</Text>
              <Text style={styles.sosButtonSubtext}>Record Audit Event & Location</Text>
            </>
          )}
        </TouchableOpacity>

        {/* Safety Disclaimer */}
        <View style={styles.disclaimerBox} accessibilityRole="summary">
          <Text style={styles.disclaimerText}>
            ⚠️ <Text style={{ fontWeight: '700' }}>DISCLAIMER:</Text> Pressing SOS records an official
            security audit log on the SafePath AI server. It does{' '}
            <Text style={{ fontWeight: '700' }}>NOT</Text> automatically dispatch police or ambulance
            services. To reach responders, use the direct call buttons below.
          </Text>
        </View>
      </Card>

      {/* Direct Emergency Helplines Section */}
      <View style={styles.sectionHeaderRow}>
        <Text style={styles.sectionTitle}>Direct Emergency Helplines (24/7)</Text>
      </View>

      <View style={styles.helplineList}>
        {OFFICIAL_HELPLINES.map((helpline: HelplineInfo) => (
          <Card key={helpline.number} style={styles.helplineCard}>
            <View style={styles.helplineLeft}>
              <View style={[styles.helplineBadge, { backgroundColor: helpline.badgeColor }]}>
                <Text style={styles.helplineNumber}>{helpline.number}</Text>
              </View>
              <View style={styles.helplineInfo}>
                <Text style={styles.helplineTitle}>{helpline.title}</Text>
                <Text style={styles.helplineCategory}>{helpline.category}</Text>
                <Text style={styles.helplineDesc}>{helpline.description}</Text>
              </View>
            </View>

            <TouchableOpacity
              style={[styles.callButton, { backgroundColor: helpline.badgeColor }]}
              onPress={() => handleCall(helpline.number, helpline.title)}
              accessibilityLabel={`Call ${helpline.title} at ${helpline.number}`}
              accessibilityRole="button"
            >
              <Text style={styles.callButtonText}>📞 Call</Text>
            </TouchableOpacity>
          </Card>
        ))}
      </View>

      {/* Trusted Emergency Contacts Section */}
      <View style={styles.sectionHeaderRow}>
        <Text style={styles.sectionTitle}>
          Trusted Contacts ({contacts.length}/5)
        </Text>
        {!showAddForm && contacts.length < 5 && (
          <TouchableOpacity
            style={styles.addContactBtn}
            onPress={() => {
              setShowAddForm(true);
              setContactPrimary(contacts.length === 0);
            }}
            accessibilityRole="button"
            accessibilityLabel="Add Emergency Contact"
          >
            <Text style={styles.addContactBtnText}>+ Add Contact</Text>
          </TouchableOpacity>
        )}
      </View>

      {/* Add Contact Form Card */}
      {showAddForm && (
        <Card style={styles.formCard}>
          <Text style={styles.formTitle}>Add Emergency Contact</Text>

          <Input
            label="Full Name *"
            placeholder="e.g. Maya Sharma"
            value={contactName}
            onChangeText={(text) => {
              setContactName(text);
              if (nameError) setNameError(undefined);
            }}
            error={nameError}
            autoCapitalize="words"
          />

          <Input
            label="Phone Number *"
            placeholder="e.g. +91 9876543210"
            value={contactPhone}
            onChangeText={(text) => {
              setContactPhone(text);
              if (phoneError) setPhoneError(undefined);
            }}
            error={phoneError}
            keyboardType="phone-pad"
          />

          <Input
            label="Relationship (Optional)"
            placeholder="e.g. Parent, Spouse, Friend"
            value={contactRelationship}
            onChangeText={setContactRelationship}
            autoCapitalize="words"
          />

          <TouchableOpacity
            style={styles.primaryToggleRow}
            onPress={() => setContactPrimary(!contactPrimary)}
            accessibilityRole="checkbox"
            accessibilityState={{ checked: contactPrimary }}
          >
            <View style={[styles.checkbox, contactPrimary && styles.checkboxChecked]}>
              {contactPrimary && <Text style={styles.checkboxCheck}>✓</Text>}
            </View>
            <Text style={styles.primaryToggleLabel}>Designate as Primary Emergency Contact</Text>
          </TouchableOpacity>

          <View style={styles.formActionsRow}>
            <Button
              title={savingContact ? 'Saving...' : 'Save Contact'}
              onPress={handleSaveContact}
              disabled={savingContact}
              style={{ flex: 1, marginRight: 8 }}
            />
            <Button
              title="Cancel"
              variant="secondary"
              onPress={() => {
                setShowAddForm(false);
                setNameError(undefined);
                setPhoneError(undefined);
              }}
              disabled={savingContact}
              style={{ flex: 1, marginLeft: 8 }}
            />
          </View>
        </Card>
      )}

      {/* Contacts List */}
      {loadingContacts ? (
        <View style={styles.loadingBox}>
          <ActivityIndicator size="small" color="#007AFF" />
          <Text style={styles.loadingText}>Loading contacts...</Text>
        </View>
      ) : contacts.length === 0 && !showAddForm ? (
        <Card style={styles.emptyContactsCard}>
          <Text style={styles.emptyContactsTitle}>No Emergency Contacts Added</Text>
          <Text style={styles.emptyContactsText}>
            Add up to 5 trusted friends or family members to quickly call or send location updates during an emergency.
          </Text>
          <Button
            title="+ Add Your First Contact"
            onPress={() => {
              setShowAddForm(true);
              setContactPrimary(true);
            }}
          />
        </Card>
      ) : (
        contacts.map((contact) => (
          <Card key={contact.id} style={styles.contactCard}>
            <View style={styles.contactHeader}>
              <View style={styles.contactNameRow}>
                <Text style={styles.contactName}>{contact.name}</Text>
                {contact.primary && (
                  <View style={styles.primaryBadge}>
                    <Text style={styles.primaryBadgeText}>PRIMARY</Text>
                  </View>
                )}
              </View>
              {contact.relationship && (
                <Text style={styles.contactRelation}>{contact.relationship}</Text>
              )}
              <Text style={styles.contactPhone}>{contact.phoneNumber}</Text>
            </View>

            <View style={styles.contactActionsRow}>
              <TouchableOpacity
                style={styles.contactCallBtn}
                onPress={() => handleCall(contact.phoneNumber, contact.name)}
                accessibilityRole="button"
                accessibilityLabel={`Call ${contact.name}`}
              >
                <Text style={styles.contactCallBtnText}>📞 Call</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={styles.contactSmsBtn}
                onPress={() => handleSendSms(contact)}
                accessibilityRole="button"
                accessibilityLabel={`Send location SMS to ${contact.name}`}
              >
                <Text style={styles.contactSmsBtnText}>💬 SMS Location</Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={styles.contactDeleteBtn}
                onPress={() => handleDeleteContact(contact)}
                accessibilityRole="button"
                accessibilityLabel={`Delete contact ${contact.name}`}
              >
                <Text style={styles.contactDeleteBtnText}>🗑️</Text>
              </TouchableOpacity>
            </View>
          </Card>
        ))
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    backgroundColor: '#F2F2F7',
    padding: 16,
    paddingTop: 50,
    paddingBottom: 40,
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  backButton: {
    paddingVertical: 8,
    paddingHorizontal: 4,
    width: 60,
  },
  backButtonText: {
    fontSize: 17,
    color: '#007AFF',
    fontWeight: '600',
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#1C1C1E',
    textAlign: 'center',
  },
  errorBanner: {
    backgroundColor: '#FFE5E5',
    borderColor: '#FF3B30',
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    marginBottom: 12,
  },
  errorBannerText: {
    color: '#D70015',
    fontSize: 13,
    lineHeight: 18,
  },
  successBanner: {
    backgroundColor: '#E8F8EE',
    borderColor: '#34C759',
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    marginBottom: 12,
  },
  successBannerText: {
    color: '#1E7B34',
    fontSize: 13,
    fontWeight: '600',
    textAlign: 'center',
  },
  sosActiveCard: {
    backgroundColor: '#FFF0F0',
    borderColor: '#FF3B30',
    borderWidth: 2,
    borderRadius: 12,
    padding: 14,
    marginBottom: 16,
  },
  sosActiveHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 10,
    marginBottom: 8,
  },
  sosActiveIcon: {
    fontSize: 28,
  },
  sosActiveTitle: {
    fontSize: 16,
    fontWeight: '800',
    color: '#D70015',
  },
  sosActiveTime: {
    fontSize: 12,
    color: '#636366',
    marginTop: 2,
  },
  sosActiveRef: {
    fontSize: 12,
    color: '#1C1C1E',
    fontWeight: '600',
    marginTop: 2,
  },
  sosActiveCoords: {
    fontSize: 11,
    color: '#3A3A3C',
    marginTop: 2,
  },
  sosActiveAdvice: {
    fontSize: 13,
    color: '#D70015',
    fontWeight: '600',
    lineHeight: 18,
    borderTopWidth: 1,
    borderTopColor: '#FFD2D2',
    paddingTop: 8,
  },
  sosCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 20,
    alignItems: 'center',
    marginBottom: 20,
    shadowColor: '#FF3B30',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.15,
    shadowRadius: 8,
    elevation: 4,
  },
  sosCardTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#1C1C1E',
    marginBottom: 16,
  },
  sosButton: {
    backgroundColor: '#FF3B30',
    width: '100%',
    paddingVertical: 22,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 14,
    shadowColor: '#FF3B30',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.35,
    shadowRadius: 10,
    elevation: 6,
  },
  sosButtonDisabled: {
    opacity: 0.7,
  },
  sosButtonIcon: {
    fontSize: 32,
    marginBottom: 4,
  },
  sosButtonText: {
    color: '#FFFFFF',
    fontSize: 20,
    fontWeight: '900',
    letterSpacing: 1,
  },
  sosButtonSubtext: {
    color: '#FFE5E5',
    fontSize: 12,
    fontWeight: '500',
    marginTop: 2,
  },
  disclaimerBox: {
    backgroundColor: '#FFF8E6',
    borderColor: '#FFD60A',
    borderWidth: 1,
    borderRadius: 8,
    padding: 10,
    width: '100%',
  },
  disclaimerText: {
    fontSize: 12,
    color: '#7A5B00',
    lineHeight: 16,
  },
  sectionHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 10,
    marginTop: 6,
  },
  sectionTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  addContactBtn: {
    backgroundColor: '#EBF4FF',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 8,
  },
  addContactBtnText: {
    fontSize: 13,
    color: '#007AFF',
    fontWeight: '600',
  },
  helplineList: {
    marginBottom: 16,
  },
  helplineCard: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 14,
    marginBottom: 8,
  },
  helplineLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
    marginRight: 10,
    gap: 12,
  },
  helplineBadge: {
    paddingHorizontal: 10,
    paddingVertical: 8,
    borderRadius: 8,
    minWidth: 50,
    alignItems: 'center',
    justifyContent: 'center',
  },
  helplineNumber: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '800',
  },
  helplineInfo: {
    flex: 1,
  },
  helplineTitle: {
    fontSize: 15,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  helplineCategory: {
    fontSize: 12,
    color: '#636366',
    fontWeight: '500',
  },
  helplineDesc: {
    fontSize: 11,
    color: '#8E8E93',
    marginTop: 2,
  },
  callButton: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 8,
  },
  callButtonText: {
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '700',
  },
  formCard: {
    padding: 16,
    marginBottom: 14,
  },
  formTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1C1C1E',
    marginBottom: 12,
  },
  primaryToggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 10,
    gap: 10,
  },
  checkbox: {
    width: 20,
    height: 20,
    borderRadius: 4,
    borderWidth: 2,
    borderColor: '#8E8E93',
    justifyContent: 'center',
    alignItems: 'center',
  },
  checkboxChecked: {
    backgroundColor: '#007AFF',
    borderColor: '#007AFF',
  },
  checkboxCheck: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '800',
  },
  primaryToggleLabel: {
    fontSize: 14,
    color: '#1C1C1E',
    fontWeight: '500',
  },
  formActionsRow: {
    flexDirection: 'row',
    marginTop: 8,
  },
  loadingBox: {
    padding: 20,
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 8,
  },
  loadingText: {
    fontSize: 13,
    color: '#8E8E93',
  },
  emptyContactsCard: {
    padding: 20,
    alignItems: 'center',
  },
  emptyContactsTitle: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1C1C1E',
    marginBottom: 6,
  },
  emptyContactsText: {
    fontSize: 13,
    color: '#636366',
    textAlign: 'center',
    lineHeight: 18,
    marginBottom: 16,
  },
  contactCard: {
    padding: 14,
    marginBottom: 10,
  },
  contactHeader: {
    marginBottom: 10,
  },
  contactNameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  contactName: {
    fontSize: 16,
    fontWeight: '700',
    color: '#1C1C1E',
  },
  primaryBadge: {
    backgroundColor: '#34C759',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  primaryBadgeText: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: '800',
  },
  contactRelation: {
    fontSize: 12,
    color: '#636366',
    marginTop: 2,
  },
  contactPhone: {
    fontSize: 14,
    color: '#3A3A3C',
    fontWeight: '500',
    marginTop: 4,
  },
  contactActionsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    borderTopWidth: 1,
    borderTopColor: '#F2F2F7',
    paddingTop: 10,
  },
  contactCallBtn: {
    flex: 1,
    backgroundColor: '#34C759',
    paddingVertical: 8,
    borderRadius: 8,
    alignItems: 'center',
  },
  contactCallBtnText: {
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '700',
  },
  contactSmsBtn: {
    flex: 1,
    backgroundColor: '#007AFF',
    paddingVertical: 8,
    borderRadius: 8,
    alignItems: 'center',
  },
  contactSmsBtnText: {
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '700',
  },
  contactDeleteBtn: {
    backgroundColor: '#F2F2F7',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
  },
  contactDeleteBtnText: {
    fontSize: 14,
  },
});
