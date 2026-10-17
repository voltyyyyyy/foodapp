"""Routing regressions using a disposable database and no external requests."""
import os
import sys
import tempfile
from pathlib import Path
from unittest.mock import AsyncMock, patch

root = Path(__file__).resolve().parents[2]
temporary = tempfile.TemporaryDirectory(prefix='foodbridge-routes-')
os.environ.update(
    DATABASE_URL='sqlite:///' + (Path(temporary.name) / 'routes.db').as_posix(),
    ADMIN_EMAIL='routing-check@example.com',
    ADMIN_PASSWORD='isolated-routing-check-2026',
    ENVIRONMENT='development',
    DEMO_DATA='true',
)
sys.path.insert(0, str(root / 'backend'))

from fastapi.testclient import TestClient
from app.main import app, routing_adapter as adapter
from app.google_routes import GoogleRoutes
from app.db import engine

try:
    assert isinstance(adapter, GoogleRoutes), 'In-app directions must use the configured Google Routes integration'
    with TestClient(app) as client:
        body = {'latitude': 1.3691, 'longitude': 103.8494, 'service_id': 'harmony', 'mode': 'walk'}
        assert client.post('/api/routes', json=body).status_code == 401
        login = client.post('/api/admin/login', json={
            'email': os.environ['ADMIN_EMAIL'], 'password': os.environ['ADMIN_PASSWORD'],
        })
        assert login.status_code == 200
        client.headers['Authorization'] = 'Bearer ' + login.json()['token']
        with patch.dict(os.environ, {'GOOGLE_ROUTES_API_KEY': ''}):
            response = client.post('/api/routes', json=body)
        assert response.status_code == 200
        assert response.json() == {
            'available': False,
            'message': 'Google routing is not connected yet. Support details are still available.',
        }
        with patch.object(adapter, 'route', new=AsyncMock(side_effect=RuntimeError('private upstream detail'))):
            failure = client.post('/api/routes', json=body).json()
        assert failure['available'] is False and 'private upstream detail' not in failure['message']
        for mode in ('walk', 'pt', 'drive'):
            result = {'available': True, 'mode': mode, 'distance_km': 1.2, 'duration_minutes': 15,
                      'geometry': [[1.3691, 103.8494], [1.37, 103.85]], 'instructions': ['Continue ahead']}
            with patch.object(adapter, 'route', new=AsyncMock(return_value=result)) as routing:
                response = client.post('/api/routes', json={**body, 'mode': mode})
            assert response.status_code == 200 and response.json() == result
            args = routing.await_args.args
            assert args[0]['latitude'] == body['latitude'] and args[0]['longitude'] == body['longitude']
            assert args[1]['name'] == 'Harmony Community Kitchen' and args[2] == mode
        assert client.post('/api/routes', json={**body, 'mode': 'invalid'}).status_code == 422
        print('Routing checks passed: authentication, missing configuration, safe failure, all three modes and geometry.')
finally:
    engine.dispose()
    temporary.cleanup()
