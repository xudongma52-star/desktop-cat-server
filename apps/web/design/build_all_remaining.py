"""Build every remaining specimen with Blender, then finish unified review assets."""
import runpy, sys
from pathlib import Path
design=Path(__file__).resolve().parent
for slug,script in [('center-bamboo-03','build_remaining_bamboo.py'),('right-bamboo-01','build_remaining_bamboo.py'),
                    ('shoot-step-05','build_remaining_shoot.py'),('shoot-step-06','build_remaining_shoot.py'),
                    ('shoot-step-07','build_remaining_shoot.py'),('shoot-step-08','build_remaining_shoot.py')]:
    sys.argv=['blender','--',slug]
    runpy.run_path(str(design/script),run_name='__main__')
