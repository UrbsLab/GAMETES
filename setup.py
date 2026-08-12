"""Compatibility shim for older pip versions that lack PEP 660 support."""

from setuptools import find_packages, setup


setup(
    name="py-gametes",
    version="2.2.0",
    description="Python port of UrbsLab GAMETES v2.2",
    packages=find_packages(),
    python_requires=">=3.9",
    extras_require={"test": ["pytest>=7"]},
    entry_points={"console_scripts": ["gametes=py_gametes.cli:main"]},
)
