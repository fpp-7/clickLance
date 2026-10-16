"""BE-002: OpenAPI, exemplos e compatibilidade com o cliente v1 real, sem rede."""
import copy
import json
from pathlib import Path
import re
import sys
import unittest
from unittest.mock import Mock, patch

import requests
import yaml
from jsonschema import Draft202012Validator, FormatChecker, ValidationError
from openapi_spec_validator import validate as validate_openapi

BACKEND = Path(__file__).resolve().parents[1]
ROOT = BACKEND.parent
sys.path.insert(0, str(ROOT / 'gateway_captura'))

from src.config import Config
from src.persistent_pipeline import CloudGateway, PermanentFailure
from src.remote_config import CAPTURE_FIELDS, FIELDS, validate as validate_gateway


class ContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = yaml.safe_load((BACKEND / 'openapi.yaml').read_text(encoding='utf-8'))
        cls.schemas = cls.spec['components']['schemas']

    def validator(self, name):
        return Draft202012Validator(
            {'$ref': f'#/components/schemas/{name}', 'components': self.spec['components']},
            format_checker=FormatChecker(),
        )

    def invalid(self, name, data):
        with self.assertRaises(ValidationError):
            self.validator(name).validate(data)

    def test_openapi_structure_and_references(self):
        validate_openapi(self.spec)

    def test_all_schema_examples(self):
        for name, schema in self.schemas.items():
            for example in schema.get('examples', []):
                with self.subTest(schema=name):
                    self.validator(name).validate(example)

    def test_existing_v1_document_examples(self):
        text = (ROOT / 'docs/contrato_cloud_gateway_v1.md').read_text(encoding='utf-8')
        examples = [json.loads(block) for block in re.findall(r'```json\s*(.*?)```', text, re.S)]
        self.assertEqual(len(examples), 3)
        for name, example in zip(('ReplayCreate', 'ReplayAck', 'GatewayConfig'), examples):
            with self.subTest(schema=name):
                self.validator(name).validate(example)
        validate_gateway(examples[-1])

    def test_configuration_allowlist_ranges_and_types_against_real_gateway(self):
        properties = self.schemas['ConfigSettings']['properties']
        self.assertEqual(set(properties), set(FIELDS) | CAPTURE_FIELDS)
        for key, (lower, upper) in FIELDS.items():
            for value in (lower, upper):
                with self.subTest(field=key, boundary=value):
                    config = {'version': 'test-v1', 'active': True, 'settings': {key: value}}
                    self.validator('GatewayConfig').validate(config)
                    validate_gateway(config)
            for value in (lower - 1, upper + 1, True, '1'):
                with self.subTest(field=key, invalid_type=type(value).__name__):
                    config = {'version': 'test-v1', 'active': True, 'settings': {key: value}}
                    self.invalid('GatewayConfig', config)
                    with self.assertRaises(ValueError):
                        validate_gateway(config)
        for settings in ({'AWS_SECRET_ACCESS_KEY': 'invalid'}, {'QUADRA_ID': 'other'}, {'CAMERA_SOURCE': None}):
            config = {'version': 'test-v1', 'active': True, 'settings': settings}
            self.invalid('GatewayConfig', config)
            with self.assertRaises(ValueError):
                validate_gateway(config)

    def test_exact_envelope_and_schema_semantic_limits_are_documented(self):
        config = copy.deepcopy(self.schemas['GatewayConfig']['examples'][0])
        config['published_by'] = 'must-not-be-in-v1'
        self.invalid('GatewayConfig', config)
        with self.assertRaises(ValueError):
            validate_gateway(config)
        for settings in ({'KEYFRAME_INTERVAL': 1.0}, {'CAMERA_RTSP_URL': 'rtsp://user@'}):
            config = {'version': 'test-v1', 'active': True, 'settings': settings}
            # JSON Schema alone cannot enforce lexical integer types or URL hostname.
            self.validator('GatewayConfig').validate(config)
            with self.assertRaises(ValueError):
                validate_gateway(config)
        description = self.schemas['ConfigSettings']['description']
        self.assertIn('1.0', description)
        self.assertIn('host', description)

    def test_admin_domain_and_no_privilege_injection(self):
        empresa = copy.deepcopy(self.schemas['EmpresaCreate']['examples'][0])
        for days in (1, 2, 30):
            self.validator('EmpresaCreate').validate(dict(empresa, retencao_dias=days))
        for days in (0, 31, '2', True):
            self.invalid('EmpresaCreate', dict(empresa, retencao_dias=days))
        self.invalid('EmpresaCreate', dict(empresa, role='PLATFORM_ADMIN'))
        self.invalid('EmpresaCreate', dict(empresa, nome='   '))
        self.validator('DeviceId').validate('mega01')
        for identifier in ('', 'x' * 33, '../mega', 'mega 01'):
            self.invalid('DeviceId', identifier)
        self.invalid('AdminLogin', {'email': 'admin@example.invalid', 'password': 'example-only', 'role': 'PLATFORM_ADMIN'})

    def test_generic_queries_cannot_contain_credential_fields(self):
        device = {'dispositivo_id': 'mega01', 'empresa_id': 'empresa_001',
                  'quadra_id': 'quadra_001', 'active': True, 'bucket': 'bucket-privado'}
        self.validator('Dispositivo').validate(device)
        for field in ('device_token', 'token_hash', 'CAMERA_RTSP_URL'):
            self.invalid('Dispositivo', dict(device, **{field: 'must-not-leak'}))
        error = copy.deepcopy(self.schemas['ApiError']['examples'][0])
        error['violations'][0]['rejected_value'] = 'must-not-leak'
        self.invalid('ApiError', error)

    def test_audit_and_error_contracts_reject_sensitive_extensions(self):
        entry = {'audit_id': '8cc5c2cc-a4b6-4a5e-8a45-574499b2e247',
                 'correlation_id': '8cc5c2cc-a4b6-4a5e-8a45-574499b2e247',
                 'occurred_at': '2026-10-16T12:00:00Z', 'actor_id': None, 'actor_type': 'ANONYMOUS',
                 'action': 'ADMIN_LOGIN', 'resource_type': 'ADMIN_SESSION', 'resource_id': None,
                 'empresa_id': None, 'outcome': 'REFUSED', 'code': 'UNAUTHENTICATED', 'http_status': 401}
        self.validator('AuditEntry').validate(entry)
        for field in ('password', 'token', 'token_hash', 'headers', 'payload', 'url', 'email'):
            self.invalid('AuditEntry', dict(entry, **{field: 'must-not-leak'}))
        self.invalid('AuditEntry', dict(entry, outcome='apparently-successful'))
        for code in ('INTERNAL_ERROR', 'UNAUTHENTICATED', 'DEPENDENCY_UNAVAILABLE'):
            self.validator('ApiError').validate({'code': code, 'message': 'Mensagem segura.', 'correlation_id': entry['correlation_id']})
        self.validator('ApiError').validate({'code': 'VALIDATION_FAILED', 'message': 'Campo invalido.',
                'correlation_id': entry['correlation_id'], 'violations': [{'field': 'bucket', 'code': 'NOT_ALLOWED'}]})
        for name, response in self.spec['components']['responses'].items():
            self.assertIn('X-Correlation-ID', response['headers'], name)

    def test_device_issuance_and_origin_fields_follow_the_administrative_contract(self):
        device = {'dispositivo_id': 'mega01', 'empresa_id': 'empresa_001',
                  'quadra_id': 'quadra_001', 'active': True, 'bucket': 'bucket-privado'}
        payload = {key: device[key] for key in ('dispositivo_id', 'active', 'bucket')}
        self.validator('DispositivoCreate').validate(payload)
        for field in ('empresa_id', 'quadra_id', 'device_token', 'token_hash', 'role'):
            self.invalid('DispositivoCreate', dict(payload, **{field: 'must-not-be-accepted'}))
        issued = {'dispositivo': device, 'device_token': 'a' * 43}
        self.validator('DispositivoIssued').validate(issued)
        for secret in ('', 'a' * 42, 'a' * 44, 'a' * 42 + '=', 'with spaces'):
            self.invalid('DispositivoIssued', dict(issued, device_token=secret))
        for device_id in ('a', '_', 'x' * 32):
            self.validator('DeviceId').validate(device_id)
        path = '/api/v1/admin/empresas/{empresa_id}/quadras/{quadra_id}/dispositivos'
        for method in ('post', 'get'):
            self.assertEqual(self.spec['paths'][path][method]['x-implementation-status'], 'implemented')

    def test_real_gateway_register_emits_contract_and_accepts_ack(self):
        payload = self.schemas['ReplayCreate']['examples'][0]
        event = {'id': payload['evento_id'], 'device': payload['dispositivo_id'],
                 'timestamp': payload['timestamp'], 'object_key': payload['objeto']['key'],
                 'sha256': payload['objeto']['sha256'],
                 'config': json.dumps({'quadra_id': payload['quadra_id'], 'bucket': payload['objeto']['bucket']})}
        for status in (200, 201):
            with self.subTest(status=status):
                response = requests.Response()
                response.status_code = status
                response._content = json.dumps(self.schemas['ReplayAck']['examples'][0]).encode()
                http = Mock()
                http.post.return_value = response
                with patch.object(Config, 'DEVICE_TOKEN', 'contract-test-only'):
                    CloudGateway(s3=object(), http=http).register(event)
                sent = http.post.call_args.kwargs
                self.validator('ReplayCreate').validate(sent['json'])
                self.assertEqual(sent['json'], payload)
                self.assertEqual(sent['headers']['Idempotency-Key'], payload['evento_id'])
                self.assertEqual(sent['headers']['X-Device-Token'], 'contract-test-only')
                self.assertFalse(sent['allow_redirects'])

    def test_gateway_preserves_terminal_and_retryable_error_classification(self):
        payload = self.schemas['ReplayCreate']['examples'][0]
        event = {'id': payload['evento_id'], 'device': payload['dispositivo_id'],
                 'timestamp': payload['timestamp'], 'object_key': payload['objeto']['key'],
                 'sha256': payload['objeto']['sha256'],
                 'config': json.dumps({'quadra_id': payload['quadra_id'], 'bucket': payload['objeto']['bucket']})}
        for status, exception in ((401, PermanentFailure), (403, PermanentFailure),
                                  (409, PermanentFailure), (422, PermanentFailure),
                                  (429, requests.HTTPError), (503, requests.HTTPError)):
            with self.subTest(status=status):
                response = requests.Response()
                response.status_code = status
                http = Mock()
                http.post.return_value = response
                with patch.object(Config, 'DEVICE_TOKEN', 'contract-test-only'):
                    with self.assertRaises(exception):
                        CloudGateway(s3=object(), http=http).register(event)

    def test_replay_rejects_missing_offset_extra_fields_and_wrong_checksum_shape(self):
        payload = copy.deepcopy(self.schemas['ReplayCreate']['examples'][0])
        self.invalid('ReplayCreate', dict(payload, timestamp='2026-10-15T15:47:57'))
        self.invalid('ReplayCreate', dict(payload, empresa_id='adulterada'))
        payload['objeto']['sha256'] = 'not-a-checksum'
        self.invalid('ReplayCreate', payload)


if __name__ == '__main__':
    if len(sys.argv) == 3 and sys.argv[1] in ('--device-responses', '--audit-responses', '--config-responses', '--replay-responses'):
        fixtures = json.loads(Path(sys.argv[2]).read_text(encoding='utf-8'))
        assert fixtures
        ContractTests.setUpClass()
        contract = ContractTests()
        for fixture in fixtures:
            contract.validator(fixture['schema']).validate(fixture['body'])
            if fixture['schema'] == 'GatewayConfig':
                validate_gateway(fixture['body'])
        print(f'{len(fixtures)} respostas HTTP validadas contra a OpenAPI: OK')
    elif len(sys.argv) == 3 and sys.argv[1] == '--capture-profiles':
        fixtures = json.loads(Path(sys.argv[2]).read_text(encoding='utf-8'))
        assert fixtures and {item['accepted'] for item in fixtures} == {True, False}
        ContractTests.setUpClass()
        contract = ContractTests()
        for index, item in enumerate(fixtures):
            envelope = {'version': 'be005-http-fixture', 'active': True, 'settings': item['settings']}
            try:
                validate_gateway(envelope)
                accepted = True
            except ValueError:
                accepted = False
            assert accepted == item['accepted'], f'Gateway divergiu da API no caso {index}'
            if accepted:
                contract.validator('CaptureProfile').validate(item['settings'])
        print(f'{len(fixtures)} perfis HTTP comparados ao validador real do gateway: OK')
    else:
        unittest.main(verbosity=2)
