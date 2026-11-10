"""Audition and stored-preset endpoints, and the session logic behind them (no hardware)."""
import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from mmp_session import MicroPlusSession
from mmp_web import dispatch, HTTPError

PRESET = {'info': {'displayName': 'CP Limelight solo'},
          'audioGraph': {'nodes': [{'nodeId': 'amp', 'FenderId': 'ACD_Twin65GT',
                                    'dspUnitParameters': {'volume': -12.0}}]}}


class FakeController:
    failure = None

    def __init__(self):
        self.slot = 19
        self.names = ['Preset {}'.format(n) for n in range(1, 101)]
        self.calls = []

    def status(self):
        return {'preset': self.slot, 'name': self.names[self.slot - 1]}

    async def switch(self, slot):
        self.calls.append(('switch', slot))
        self.slot = slot

    async def audition(self, doc):
        self.calls.append(('audition', doc['info']['displayName']))
        return self.slot

    async def stored_preset(self, slot):
        self.calls.append(('stored', slot))
        return {'info': {'displayName': self.names[slot - 1]}, 'audioGraph': {'nodes': []}}


class FakeClient:
    is_connected = True


def connected_session():
    session = MicroPlusSession()
    session.controller, session.client = FakeController(), FakeClient()
    session.ready.set()
    return session


class AuditionEndpointTests(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.headers = {'host': 'localhost:8081', 'origin': 'http://localhost:8081',
                        'content-type': 'application/json'}

    async def post(self, session, request, path='/api/micro-plus/audition'):
        return await dispatch('POST', path, self.headers, json.dumps(request).encode(), session)

    async def test_audition_on_reserved_slot(self):
        session = connected_session()
        code, _, body = await self.post(session, {'preset': PRESET, 'slot': 100})
        state = json.loads(body)
        self.assertEqual(code, 200)
        self.assertEqual(session.controller.calls, [('switch', 100), ('audition', 'CP Limelight solo')])
        self.assertEqual(state['audition'], {'name': 'CP Limelight solo', 'slot': 100})
        self.assertEqual((state['preset'], state['name']), (100, 'CP Limelight solo'))
        # selecting a real preset ends the audition
        await dispatch('POST', '/api/micro-plus', self.headers, b'{"preset": 19}', session)
        _, _, body = await dispatch('GET', '/api/micro-plus', self.headers, b'', session)
        self.assertIsNone(json.loads(body)['audition'])

    async def test_audition_without_slot_stays_on_current(self):
        session = connected_session()
        await self.post(session, {'preset': PRESET})
        self.assertEqual(session.controller.calls, [('audition', 'CP Limelight solo')])

    async def test_invalid_auditions_rejected_before_bluetooth(self):
        session = connected_session()
        for request in ({}, {'preset': 'x'}, {'preset': {'audioGraph': {'nodes': []}}},
                        {'preset': PRESET, 'slot': 101}, {'preset': PRESET, 'slot': '100'},
                        {'preset': PRESET, 'extra': 1},
                        {'preset': dict(PRESET, pad='x' * 70000)}):
            with self.subTest(request=str(request)[:60]), self.assertRaises(HTTPError) as caught:
                await self.post(session, request)
            self.assertEqual(caught.exception.status, 400)
        self.assertEqual(session.controller.calls, [])

    async def test_foreign_origin_and_wrong_method(self):
        session = connected_session()
        self.headers['origin'] = 'https://example.com'
        with self.assertRaises(HTTPError) as caught:
            await self.post(session, {'preset': PRESET})
        self.assertEqual(caught.exception.status, 403)
        with self.assertRaises(HTTPError) as caught:
            await dispatch('GET', '/api/micro-plus/audition', self.headers, b'', session)
        self.assertEqual(caught.exception.status, 405)

    async def test_disconnected(self):
        with self.assertRaises(HTTPError) as caught:
            await self.post(MicroPlusSession(), {'preset': PRESET})
        self.assertEqual(caught.exception.status, 503)

    async def test_stored_preset_is_cached(self):
        session = connected_session()
        for _ in range(2):
            code, _, body = await dispatch('GET', '/api/micro-plus/preset', self.headers, b'',
                                           session, query='slot=21')
            self.assertEqual((code, json.loads(body)['info']['displayName']), (200, 'Preset 21'))
        self.assertEqual(session.controller.calls, [('stored', 21)])
        for query in ('', 'slot=0', 'slot=101', 'slot=x', 'slot=1&slot=2'):
            with self.subTest(query=query), self.assertRaises(HTTPError) as caught:
                await dispatch('GET', '/api/micro-plus/preset', self.headers, b'', session, query=query)
            self.assertEqual(caught.exception.status, 400)


if __name__ == '__main__':
    unittest.main()
