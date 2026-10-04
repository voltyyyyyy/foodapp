import asyncio
import os
import time
from datetime import datetime, timedelta, timezone

import httpx

class OneMap:
    def __init__(self):
        self.token = None
        self.expiry = 0
        self.lock = asyncio.Lock()

    async def get_token(self, client):
        async with self.lock:
            if self.token and time.time() < self.expiry - 60:
                return self.token
            email, password = os.getenv('ONEMAP_EMAIL'), os.getenv('ONEMAP_PASSWORD')
            if not email or not password:
                raise RuntimeError('OneMap routing credentials are not configured')
            response = await client.post('https://www.onemap.gov.sg/api/auth/post/getToken', json={'email': email, 'password': password})
            response.raise_for_status()
            data = response.json()
            self.token, self.expiry = data['access_token'], float(data['expiry_timestamp'])
            return self.token

    async def geocode(self, query):
        async with httpx.AsyncClient(timeout=3.5) as client:
            response = await client.get('https://www.onemap.gov.sg/api/common/elastic/search', params={
                'searchVal': query, 'returnGeom': 'Y', 'getAddrDetails': 'Y', 'pageNum': 1})
            response.raise_for_status()
            return [{'address': r['ADDRESS'], 'postal': r.get('POSTAL', ''), 'latitude': float(r['LATITUDE']),
                'longitude': float(r['LONGITUDE'])} for r in response.json().get('results', [])][:10]

    async def route(self, origin, destination, mode):
        async with httpx.AsyncClient(timeout=3.5) as client:
            token = await self.get_token(client)
            params = {'start': f"{origin['latitude']},{origin['longitude']}",
                'end': f"{destination['latitude']},{destination['longitude']}", 'routeType': mode}
            if mode == 'pt':
                departure = datetime.now(timezone(timedelta(hours=8)))
                params.update(date=departure.strftime('%m-%d-%Y'), time=departure.strftime('%H:%M:%S'),
                    mode='TRANSIT', maxWalkDistance=1000, numItineraries=1)
            response = await client.get('https://www.onemap.gov.sg/api/public/routingsvc/route', headers={'Authorization': token},
                params=params)
            if response.status_code == 401:
                self.expiry = 0
            response.raise_for_status()
            data = response.json()
            if mode == 'pt':
                itinerary = min(data['plan']['itineraries'], key=lambda entry: entry['duration'])
                geometry, instructions = [], []
                for leg in itinerary['legs']:
                    points = decode_polyline(leg.get('legGeometry', {}).get('points', ''))
                    if not points and leg.get('distance', 0) > 0:
                        raise ValueError('Transit leg has no route geometry')
                    geometry.extend(points)
                    label = 'Walk' if leg['mode'] == 'WALK' else f"{leg['mode'].title()} {leg.get('route', '')}".strip()
                    instructions.append(f"{label}: {leg['from']['name']} to {leg['to']['name']}")
                if len(geometry) < 2:
                    raise ValueError('Transit route has no geometry')
                return {'available': True, 'mode': mode,
                    'distance_km': sum(leg.get('distance', 0) for leg in itinerary['legs']) / 1000,
                    'duration_minutes': itinerary['duration'] / 60, 'geometry': geometry,
                    'instructions': instructions, 'transfers': itinerary.get('transfers', 0),
                    'attribution': 'OneMap · Singapore Land Authority', 'departure': departure.isoformat()}
            summary = data['route_summary']
            return {'available': True, 'mode': mode, 'distance_km': summary['total_distance'] / 1000,
                'duration_minutes': summary['total_time'] / 60, 'geometry': decode_polyline(data['route_geometry']),
                'instructions': [step[9] for step in data.get('route_instructions', []) if len(step) > 9],
                'attribution': 'OneMap · Singapore Land Authority'}

def decode_polyline(encoded):
    result, index, lat, lon = [], 0, 0, 0
    while index < len(encoded):
        values = []
        for _ in range(2):
            shift, value = 0, 0
            while True:
                number = ord(encoded[index]) - 63
                index += 1
                value |= (number & 31) << shift
                shift += 5
                if number < 32:
                    break
            values.append(~(value >> 1) if value & 1 else value >> 1)
        lat += values[0]
        lon += values[1]
        result.append([lat / 1e5, lon / 1e5])
    return result

adapter = OneMap()
