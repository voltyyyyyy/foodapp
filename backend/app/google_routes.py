"""Google Routes adapter. Credentials and route results never enter persisted records."""
import os
from datetime import datetime, timezone

import httpx
from .onemap import decode_polyline


class GoogleRoutes:
    async def route(self, origin, destination, mode):
        key = os.getenv('GOOGLE_ROUTES_API_KEY', '').strip()
        if not key:
            return {'available': False, 'message': 'Google routing is not connected yet. Support details are still available.'}
        travel_mode = {'walk': 'WALK', 'pt': 'TRANSIT', 'drive': 'DRIVE'}[mode]
        body = {
            'origin': {'location': {'latLng': {'latitude': origin['latitude'], 'longitude': origin['longitude']}}},
            'destination': {'location': {'latLng': {'latitude': destination['latitude'], 'longitude': destination['longitude']}}},
            'travelMode': travel_mode, 'languageCode': 'en', 'units': 'METRIC',
            'polylineQuality': 'HIGH_QUALITY', 'polylineEncoding': 'ENCODED_POLYLINE',
        }
        if mode == 'pt':
            body['departureTime'] = datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z')
        fields = 'routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,routes.legs.steps.navigationInstruction,routes.legs.steps.transitDetails,routes.warnings'
        async with httpx.AsyncClient(timeout=3.5) as client:
            response = await client.post('https://routes.googleapis.com/directions/v2:computeRoutes', json=body,
                headers={'X-Goog-Api-Key': key, 'X-Goog-FieldMask': fields})
            response.raise_for_status()
            data = response.json()
        routes = data.get('routes', [])
        if not routes:
            return {'available': False, 'message': 'No route found for this travel option. Try a different option or starting address.'}
        route = routes[0]
        geometry = decode_polyline(route['polyline']['encodedPolyline'])
        if len(geometry) < 2:
            raise ValueError('Route geometry missing')
        instructions, agencies, transit_legs = [], {}, 0
        for leg in route.get('legs', []):
            for step in leg.get('steps', []):
                transit = step.get('transitDetails')
                if transit:
                    transit_legs += 1
                    line = transit.get('transitLine', {})
                    stops = transit.get('stopDetails', {})
                    label = line.get('nameShort') or line.get('name') or 'Public transport'
                    vehicle = line.get('vehicle', {}).get('name', {}).get('text', '')
                    start = stops.get('departureStop', {}).get('name', '')
                    end = stops.get('arrivalStop', {}).get('name', '')
                    text = f"{vehicle} {label}: {start} to {end}".strip()
                    headsign = transit.get('headsign')
                    if headsign:
                        text += f" (towards {headsign})"
                    if transit.get('stopCount'):
                        text += f" ? {transit['stopCount']} stops"
                    instructions.append(text)
                    for agency in line.get('agencies', []):
                        if agency.get('name'):
                            agencies[agency['name']] = {'name': agency['name'], 'url': agency.get('uri', '')}
                else:
                    text = step.get('navigationInstruction', {}).get('instructions')
                    if text:
                        instructions.append(text)
        return {'available': True, 'mode': mode, 'distance_km': route.get('distanceMeters', 0) / 1000,
            'duration_minutes': float(route['duration'].removesuffix('s')) / 60,
            'geometry': geometry, 'instructions': instructions, 'transfers': max(0, transit_legs - 1),
            'attribution': 'Google Maps', 'agencies': list(agencies.values()), 'warnings': route.get('warnings', [])}


adapter = GoogleRoutes()
