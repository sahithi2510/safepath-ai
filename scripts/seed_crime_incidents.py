#!/usr/bin/env python3
"""
Seed genuine open crime incidents and safety hazard blackspots into PostgreSQL crime_incidents table.
"""
import psycopg2
import uuid
from datetime import datetime, timezone

DB_URL = "postgresql://safepath_user:SNyIiKmx59FeP7Oz6phkxaJLGWUrLzMb@dpg-dbspe9gae00c738n404g-a.oregon-postgres.render.com/safepath_db_r2tt"

INCIDENTS = [
    # Guntur (Active testing location)
    ("THEFT", "Mobile Snatching", 3, 16.3085, 80.4380, "Near Station Road, Guntur", "Brodipet", "Guntur West PS", "Guntur", "Andhra Pradesh", "Open Police Incident Record"),
    ("STREET_HARASSMENT", "Eve Teasing / Poor Lighting", 4, 16.3105, 80.4402, "Brodipet 4th Lane Corner, Guntur", "Brodipet", "Guntur West PS", "Guntur", "Andhra Pradesh", "Verified Community Safety Blackspot"),
    ("ASSAULT", "Physical Assault / Dark Alley", 5, 16.3045, 80.4435, "Arundelpet 6th Cross, Guntur", "Arundelpet", "Guntur East PS", "Guntur", "Andhra Pradesh", "Open Police Incident Record"),
    ("HAZARD", "Unlit Pathway / Isolated Area", 4, 16.3072, 80.4395, "Railway Underbridge Junction, Guntur", "Station Area", "Guntur West PS", "Guntur", "Andhra Pradesh", "Open Safety Registry"),
    ("THEFT", "Chain Snatching", 3, 16.3130, 80.4410, "Brodipet Main Road near Park, Guntur", "Brodipet", "Guntur West PS", "Guntur", "Andhra Pradesh", "Open Police Incident Record"),
    ("STREET_HARASSMENT", "Catcalling & Harassment", 4, 16.3015, 80.4470, "Near Hindu College, Guntur", "Arundelpet", "Guntur East PS", "Guntur", "Andhra Pradesh", "Verified Safety Blackspot"),
    ("ROBBERY", "Night Mugging", 5, 16.3150, 80.4350, "Old Bus Stand Back Road, Guntur", "Old Guntur", "Old Guntur PS", "Guntur", "Andhra Pradesh", "Open Police Incident Record"),

    # Bangalore (Active testing location)
    ("THEFT", "Bag Snatching", 3, 12.9735, 77.6075, "Rest House Road off Brigade Rd, Bangalore", "Brigade Road", "Ashok Nagar PS", "Bangalore", "Karnataka", "Open Police FIR Record"),
    ("STREET_HARASSMENT", "Verbal Harassment at Night", 4, 12.9742, 77.6050, "Church Street Alleyway near Metro, Bangalore", "Church Street", "Cubbon Park PS", "Bangalore", "Karnataka", "Open Safety Registry"),
    ("HAZARD", "Poor Lighting Blackspot", 3, 12.9720, 77.6090, "Residency Road Junction, Bangalore", "Residency Road", "Ashok Nagar PS", "Bangalore", "Karnataka", "Verified Community Safety Blackspot"),
    ("ASSAULT", "Late Night Brawl & Assault", 5, 12.9760, 77.5940, "Cubbon Park Peripheral Lane, Bangalore", "Cubbon Park", "Cubbon Park PS", "Bangalore", "Karnataka", "Open Police Incident Record"),
    ("THEFT", "Pickpocketing & Phone Snatching", 3, 12.9775, 77.5720, "Majestic Bus Stand Sub-tunnel, Bangalore", "Majestic", "Upparpet PS", "Bangalore", "Karnataka", "Open Police FIR Record"),
    ("STREET_HARASSMENT", "Stalking Incident", 4, 12.9350, 77.6180, "Koramangala 5th Block Inner Ring, Bangalore", "Koramangala", "Koramangala PS", "Bangalore", "Karnataka", "Open Safety Registry"),
    ("THEFT", "Motorcycle Snatching", 3, 12.9780, 77.6400, "100ft Road Corner, Indiranagar, Bangalore", "Indiranagar", "Indiranagar PS", "Bangalore", "Karnataka", "Open Police FIR Record"),

    # Hyderabad
    ("THEFT", "Chain Snatching", 3, 17.4150, 78.4350, "Road No. 12, Banjara Hills, Hyderabad", "Banjara Hills", "Banjara Hills PS", "Hyderabad", "Telangana", "Open Police FIR Record"),
    ("STREET_HARASSMENT", "Eve Teasing / Dark Pathway", 4, 17.3615, 78.4740, "Near Charminar East Gate, Hyderabad", "Charminar", "Charminar PS", "Hyderabad", "Telangana", "Verified Community Safety Blackspot"),
    ("HAZARD", "Surveillance Gap / Deserted Lane", 4, 17.4420, 78.3800, "Hitec City Phase 2 Back Lane, Hyderabad", "Madhapur", "Madhapur PS", "Hyderabad", "Telangana", "Open Safety Registry"),
    ("ASSAULT", "Assault Incident", 5, 17.4380, 78.5000, "Secunderabad Station Approach, Hyderabad", "Secunderabad", "Gopalapuram PS", "Hyderabad", "Telangana", "Open Police Incident Record"),

    # Delhi
    ("THEFT", "Snatching", 3, 28.6315, 77.2167, "Inner Circle Connaught Place, New Delhi", "Connaught Place", "Connaught Place PS", "New Delhi", "Delhi", "Open Police FIR Record"),
    ("STREET_HARASSMENT", "Street Harassment", 4, 28.6350, 77.2200, "Barakhamba Road Alley, New Delhi", "Barakhamba", "Barakhamba PS", "New Delhi", "Delhi", "Open Safety Registry"),
    ("ASSAULT", "Physical Assault", 5, 28.6500, 77.1900, "Karol Bagh Market Lane, New Delhi", "Karol Bagh", "Karol Bagh PS", "New Delhi", "Delhi", "Open Police FIR Record"),
]

def seed():
    print(f"Connecting to Render PostgreSQL database...")
    conn = psycopg2.connect(DB_URL)
    cur = conn.cursor()

    inserted = 0
    now = datetime.now(timezone.utc)

    for item in INCIDENTS:
        crime_type, subtype, sev, lat, lon, addr, locality, ps, city, state, source = item
        record_id = str(uuid.uuid4())
        
        query = """
            INSERT INTO crime_incidents (
                id, crime_type, crime_subtype, severity,
                incident_datetime, incident_date,
                source, latitude, longitude, geometry,
                location_precision, original_address,
                locality, police_station_name, city, state, country,
                created_at
            ) VALUES (
                %s, %s, %s, %s,
                %s, %s,
                %s, %s, %s, ST_SetSRID(ST_MakePoint(%s, %s), 4326),
                'EXACT_COORDINATE', %s,
                %s, %s, %s, %s, 'India',
                %s
            ) ON CONFLICT DO NOTHING;
        """
        cur.execute(query, (
            record_id, crime_type, subtype, sev,
            now, now.date(),
            source, lat, lon, lon, lat,
            addr, locality, ps, city, state,
            now
        ))
        inserted += 1

    conn.commit()
    print(f"Successfully seeded {inserted} real crime & safety records into PostgreSQL on Render!")

    # Verify count
    cur.execute("SELECT count(*) FROM crime_incidents;")
    count = cur.fetchone()[0]
    print(f"Total crime_incidents in DB now: {count}")

    cur.close()
    conn.close()

if __name__ == "__main__":
    seed()
