/*
 * Conditions, and the sessions built for each.
 *
 * A session is an ordered list of {p: poseId, sec: seconds}. It is written as
 * the ideal sequence for someone with no restrictions; js/safety.js then edits
 * it per user, substituting or dropping whatever their health flags rule out.
 * That is why sessions here can contain strong poses - the filter is what makes
 * them safe, not self-censorship at authoring time.
 *
 * `hero` names a pose whose illustration represents the condition on its card.
 * `autoFlags` are safety flags implied by picking the condition at all: a user
 * who chooses Pregnancy has, by choosing it, told us they are pregnant.
 *
 * Wording rule for everything user-facing in this file: these practices support
 * and relieve. They do not treat or cure, and nothing here should read as if
 * they do.
 */
(function (YG) {
  'use strict';

  YG.CONDITIONS = [

    {
      id: 'respiratory', name: 'Breathing & Lungs', hero: 'bhujangasana',
      tagline: 'Asthma, breathlessness, congestion',
      about: 'Chest-opening postures make room for the lungs to expand, and breathing practice ' +
             'retrains a diaphragm that most adults have stopped using fully. Together they tend to ' +
             'show up as easier, quieter breathing rather than as any sudden change.',
      why: [
        'Backbends open the front of the chest that habitual hunching closes down.',
        'Slow nasal breathing warms and filters air, and lengthens the exhale.',
        'Alternate nostril breathing is the most consistently useful practice here.'
      ],
      safety: [
        'Never practise during an acute asthma attack - use your reliever inhaler.',
        'Cooling breaths draw cold air straight in and can set off a reactive airway.',
        'Build up the forceful practices slowly, or leave them out entirely.'
      ],
      autoFlags: [],
      sessions: [
        {
          id: 'resp_daily', name: 'Daily Breath Opener', level: 1,
          note: 'Best on an empty stomach, morning or evening.',
          steps: [
            { p: 'joint_rotations', sec: 60 }, { p: 'shoulder_rolls', sec: 60 },
            { p: 'urdhva_hastasana', sec: 30 }, { p: 'wall_chest_opener', sec: 30 },
            { p: 'marjari_bitilasana', sec: 60 }, { p: 'bhujangasana', sec: 25 },
            { p: 'setu_bandhasana', sec: 30 }, { p: 'deep_breathing', sec: 120 },
            { p: 'anulom_vilom', sec: 180 }, { p: 'savasana', sec: 150 }
          ]
        },
        {
          id: 'resp_congestion', name: 'Clear a Blocked Chest', level: 2,
          note: 'For the tail end of a cold, once the fever has gone.',
          steps: [
            { p: 'shoulder_rolls', sec: 60 }, { p: 'simhasana', sec: 40 },
            { p: 'ardha_chakrasana', sec: 20 }, { p: 'gomukhasana', sec: 30 },
            { p: 'bhujangasana', sec: 25 }, { p: 'ustrasana', sec: 20 },
            { p: 'matsyasana', sec: 25 }, { p: 'bhastrika', sec: 45 },
            { p: 'anulom_vilom', sec: 150 }, { p: 'savasana', sec: 150 }
          ]
        },
        {
          id: 'resp_short', name: 'Five-Minute Breath Reset', level: 1,
          note: 'Enough on a day with no time for anything longer.',
          steps: [
            { p: 'shoulder_rolls', sec: 45 }, { p: 'seated_side_bend', sec: 30 },
            { p: 'deep_breathing', sec: 120 }, { p: 'anulom_vilom', sec: 120 }
          ]
        }
      ]
    },

    {
      id: 'pregnancy', name: 'Pregnancy', hero: 'marjari_bitilasana',
      tagline: 'Trimester by trimester',
      about: 'Prenatal practice is mostly about making room, keeping the hips and pelvic floor mobile, ' +
             'and having a breath to fall back on. What is safe changes as the pregnancy goes on, so the ' +
             'sessions here are split by trimester rather than offered as one prenatal routine.',
      why: [
        'Cat-Cow relieves the load on the lower back and encourages a good position for the baby.',
        'Hip and pelvic openers prepare the body for labour.',
        'Breathing practice gives you something concrete to use during contractions.'
      ],
      safety: [
        'Talk to your doctor or midwife before starting, and stop if anything hurts.',
        'No lying flat on the back after about 16 weeks - use the left side instead.',
        'No prone poses, no deep closed twists, no inversions, no breath retention, no Kapalabhati or Bhastrika.',
        'Ligaments are looser than usual, so a stretch that feels available may still be too far.',
        'Stop and seek advice for bleeding, leaking fluid, contractions, dizziness or reduced movements.'
      ],
      autoFlags: ['pregnancy'],
      sessions: [
        {
          id: 'preg_t1', name: 'First Trimester', level: 1,
          note: 'Weeks 1-13. Gentle. On days with heavy nausea or fatigue, do the breathing only.',
          steps: [
            { p: 'sukhasana', sec: 60 }, { p: 'joint_rotations', sec: 60 },
            { p: 'neck_rolls', sec: 45 }, { p: 'shoulder_rolls', sec: 45 },
            { p: 'seated_side_bend', sec: 30 }, { p: 'marjari_bitilasana', sec: 60 },
            { p: 'baddha_konasana', sec: 60 }, { p: 'vrikshasana', sec: 30 },
            { p: 'deep_breathing', sec: 120 }, { p: 'side_lying_rest', sec: 180 }
          ]
        },
        {
          id: 'preg_t2', name: 'Second Trimester', level: 1,
          note: 'Weeks 14-27. Usually the most energetic stretch - still no lying flat on the back.',
          steps: [
            { p: 'chair_sit', sec: 45 }, { p: 'shoulder_rolls', sec: 45 },
            { p: 'seated_side_bend', sec: 30 }, { p: 'marjari_bitilasana', sec: 90 },
            { p: 'pelvic_tilts', sec: 60 }, { p: 'baddha_konasana', sec: 60 },
            { p: 'virabhadrasana2', sec: 30 }, { p: 'vrikshasana', sec: 30 },
            { p: 'malasana', sec: 40 }, { p: 'legs_on_chair', sec: 120 },
            { p: 'anulom_vilom', sec: 150 }, { p: 'side_lying_rest', sec: 180 }
          ]
        },
        {
          id: 'preg_t3', name: 'Third Trimester', level: 1,
          note: 'Week 28 onwards. Short, well supported, and heavy on the breathing you will actually use.',
          steps: [
            { p: 'chair_sit', sec: 45 }, { p: 'neck_rolls', sec: 45 },
            { p: 'shoulder_rolls', sec: 45 }, { p: 'seated_side_bend', sec: 30 },
            { p: 'marjari_bitilasana', sec: 90 }, { p: 'pelvic_tilts', sec: 60 },
            { p: 'malasana', sec: 40 }, { p: 'legs_on_chair', sec: 150 },
            { p: 'deep_breathing', sec: 150 }, { p: 'bhramari', sec: 120 },
            { p: 'side_lying_rest', sec: 240 }
          ]
        }
      ]
    },

    {
      id: 'digestion', name: 'Digestion & Constipation', hero: 'pawanmuktasana',
      tagline: 'Constipation, bloating, sluggish gut',
      about: 'Twists, forward folds and knee-to-chest positions apply direct mechanical pressure along ' +
             'the digestive tract, and the abdominal breathing practices work the same area from the ' +
             'inside. This is one of the areas where yoga tends to produce results quickly.',
      why: [
        'Wind-Relieving Pose does exactly what its name says - direct pressure along the colon.',
        'Twists compress and then release the abdominal organs.',
        'A deep squat is the position the bowel actually empties best from.'
      ],
      safety: [
        'Practise on an empty stomach - first thing in the morning is ideal.',
        'A glass of warm water before you start helps considerably.',
        'Vajrasana is the one exception: it is meant to be done just after eating.',
        'Persistent constipation with pain, blood or weight loss needs a doctor, not a yoga mat.'
      ],
      autoFlags: [],
      sessions: [
        {
          id: 'dig_morning', name: 'Morning Constipation Routine', level: 2,
          note: 'On an empty stomach, ideally before going to the toilet.',
          steps: [
            { p: 'joint_rotations', sec: 45 }, { p: 'marjari_bitilasana', sec: 60 },
            { p: 'pawanmuktasana', sec: 40 }, { p: 'supta_matsyendrasana', sec: 45 },
            { p: 'bhujangasana', sec: 25 }, { p: 'dhanurasana', sec: 20 },
            { p: 'malasana', sec: 60 }, { p: 'ardha_matsyendrasana', sec: 30 },
            { p: 'paschimottanasana', sec: 40 }, { p: 'kapalabhati', sec: 60 },
            { p: 'savasana', sec: 120 }
          ]
        },
        {
          id: 'dig_bloat', name: 'Bloating Relief', level: 1,
          note: 'Gentle enough to do an hour or two after a meal.',
          steps: [
            { p: 'vajrasana', sec: 120 }, { p: 'seated_side_bend', sec: 30 },
            { p: 'marjari_bitilasana', sec: 60 }, { p: 'balasana', sec: 60 },
            { p: 'pawanmuktasana', sec: 40 }, { p: 'supta_matsyendrasana', sec: 45 },
            { p: 'deep_breathing', sec: 120 }
          ]
        },
        {
          id: 'dig_strong', name: 'Strong Abdominal Practice', level: 3,
          note: 'Empty stomach only. Skip on any day the belly feels tender.',
          steps: [
            { p: 'marjari_bitilasana', sec: 60 }, { p: 'uttanpadasana', sec: 20 },
            { p: 'pawanmuktasana', sec: 40 }, { p: 'bhujangasana', sec: 25 },
            { p: 'salabhasana', sec: 20 }, { p: 'dhanurasana', sec: 20 },
            { p: 'ardha_matsyendrasana', sec: 30 }, { p: 'malasana', sec: 45 },
            { p: 'agnisar', sec: 60 }, { p: 'kapalabhati', sec: 60 },
            { p: 'savasana', sec: 150 }
          ]
        }
      ]
    },

    {
      id: 'back', name: 'Back Pain & Sciatica', hero: 'setu_bandhasana',
      tagline: 'Lower back, stiffness, sciatic pain',
      about: 'Most everyday back pain responds to two things: restoring movement to a spine that has ' +
             'stopped moving, and strengthening what holds it up. The sequences here move gently through ' +
             'every direction rather than stretching hard in one.',
      why: [
        'Cat-Cow and pelvic tilts restore segment-by-segment movement with almost no load.',
        'Bridge builds the glutes and back extensors that take load off the spine.',
        'Reclined twists release the lower back without any effort from you.'
      ],
      safety: [
        'Sharp, shooting or radiating pain means stop and see a doctor.',
        'With a diagnosed disc problem, leave out forward folds and deep twists.',
        'Numbness, weakness or loss of bladder control is an emergency, not a yoga problem.'
      ],
      autoFlags: [],
      sessions: [
        {
          id: 'back_gentle', name: 'Gentle Daily Release', level: 1,
          note: 'Safe to do daily, including on a sore day.',
          steps: [
            { p: 'pelvic_tilts', sec: 60 }, { p: 'marjari_bitilasana', sec: 90 },
            { p: 'balasana', sec: 60 }, { p: 'pawanmuktasana', sec: 40 },
            { p: 'supta_matsyendrasana', sec: 45 }, { p: 'setu_bandhasana', sec: 30 },
            { p: 'legs_on_chair', sec: 150 }, { p: 'deep_breathing', sec: 120 }
          ]
        },
        {
          id: 'back_strength', name: 'Build Back Strength', level: 2,
          note: 'For days without acute pain. Strength is what stops it coming back.',
          steps: [
            { p: 'marjari_bitilasana', sec: 60 }, { p: 'pelvic_tilts', sec: 60 },
            { p: 'setu_bandhasana', sec: 30 }, { p: 'salabhasana', sec: 20 },
            { p: 'bhujangasana', sec: 25 }, { p: 'balasana', sec: 45 },
            { p: 'virabhadrasana2', sec: 30 }, { p: 'trikonasana', sec: 30 },
            { p: 'supta_matsyendrasana', sec: 45 }, { p: 'savasana', sec: 150 }
          ]
        },
        {
          id: 'back_sciatica', name: 'Sciatic Relief', level: 1,
          note: 'Slow and small. Back off from anything that increases the leg pain.',
          steps: [
            { p: 'pelvic_tilts', sec: 60 }, { p: 'pawanmuktasana', sec: 45 },
            { p: 'ananda_balasana', sec: 45 }, { p: 'supta_matsyendrasana', sec: 60 },
            { p: 'marjari_bitilasana', sec: 60 }, { p: 'balasana', sec: 60 },
            { p: 'legs_on_chair', sec: 180 }, { p: 'deep_breathing', sec: 120 }
          ]
        }
      ]
    },

    {
      id: 'pcos', name: 'PCOS & Menstrual Health', hero: 'baddha_konasana',
      tagline: 'PCOS, cramps, irregular cycles',
      about: 'Practice here works on two fronts: hip and pelvic openers that improve circulation through ' +
             'the reproductive organs, and stress reduction, which matters more for hormonal balance than ' +
             'it first appears to.',
      why: [
        'Butterfly and Reclined Butterfly open the pelvis and are restful enough to hold for a long time.',
        'Twists and mild backbends stimulate the abdominal organs.',
        'Slow breathing lowers cortisol, which is directly relevant to cycle regularity.'
      ],
      safety: [
        'Many people skip inversions during menstruation - the app respects that if you set it in your profile.',
        'During cramps, stay with the restorative poses and leave the strong abdominal work.',
        'Yoga supports treatment for PCOS. It does not replace medical care.'
      ],
      autoFlags: [],
      sessions: [
        {
          id: 'pcos_hormone', name: 'Hormone Balance Practice', level: 2,
          note: 'Three or four times a week, away from the heaviest days of your cycle.',
          steps: [
            { p: 'marjari_bitilasana', sec: 60 }, { p: 'baddha_konasana', sec: 60 },
            { p: 'upavistha_konasana', sec: 45 }, { p: 'bhujangasana', sec: 25 },
            { p: 'dhanurasana', sec: 20 }, { p: 'setu_bandhasana', sec: 30 },
            { p: 'ardha_matsyendrasana', sec: 30 }, { p: 'malasana', sec: 45 },
            { p: 'viparita_karani', sec: 180 }, { p: 'anulom_vilom', sec: 150 },
            { p: 'savasana', sec: 150 }
          ]
        },
        {
          id: 'pcos_cramps', name: 'Period Cramp Relief', level: 1,
          note: 'All restorative. Nothing here asks anything of you.',
          steps: [
            { p: 'balasana', sec: 90 }, { p: 'marjari_bitilasana', sec: 60 },
            { p: 'supta_baddha_konasana', sec: 180 }, { p: 'pawanmuktasana', sec: 45 },
            { p: 'supta_matsyendrasana', sec: 60 }, { p: 'legs_on_chair', sec: 180 },
            { p: 'bhramari', sec: 120 }, { p: 'yoga_nidra', sec: 240 }
          ]
        }
      ]
    },

    {
      id: 'thyroid', name: 'Thyroid', hero: 'setu_bandhasana',
      tagline: 'Supporting thyroid function',
      about: 'The traditional approach works the throat area through stretch and compression, and pairs ' +
             'it with breathing practice. Treat it as support alongside medication and testing, never ' +
             'instead of them.',
      why: [
        'Shoulder Stand and Bridge compress and then flood the throat area.',
        'Fish Pose and Camel stretch the front of the throat in the opposite direction.',
        'Lion Pose works the throat directly and takes ten seconds to learn.'
      ],
      safety: [
        'Keep taking your medication and keep testing. Nothing here changes that.',
        'Shoulder Stand carries real risk for the neck - the app leaves it out unless your profile allows it.',
        'Bridge is a safer route to most of the same effect.'
      ],
      autoFlags: [],
      sessions: [
        {
          id: 'thy_daily', name: 'Daily Thyroid Support', level: 2,
          note: 'Empty stomach, morning.',
          steps: [
            { p: 'shoulder_rolls', sec: 45 }, { p: 'marjari_bitilasana', sec: 60 },
            { p: 'simhasana', sec: 40 }, { p: 'setu_bandhasana', sec: 30 },
            { p: 'sarvangasana', sec: 45 }, { p: 'halasana', sec: 30 },
            { p: 'matsyasana', sec: 25 }, { p: 'ustrasana', sec: 20 },
            { p: 'paschimottanasana', sec: 40 }, { p: 'anulom_vilom', sec: 150 },
            { p: 'savasana', sec: 150 }
          ]
        },
        {
          id: 'thy_gentle', name: 'Gentle Throat Practice', level: 1,
          note: 'No inversions at all - the version for anyone with a neck or blood pressure concern.',
          steps: [
            { p: 'shoulder_rolls', sec: 45 }, { p: 'marjari_bitilasana', sec: 60 },
            { p: 'simhasana', sec: 40 }, { p: 'setu_bandhasana', sec: 30 },
            { p: 'bhujangasana', sec: 25 }, { p: 'legs_on_chair', sec: 150 },
            { p: 'bhramari', sec: 120 }, { p: 'savasana', sec: 150 }
          ]
        }
      ]
    },

    {
      id: 'diabetes', name: 'Diabetes', hero: 'ardha_matsyendrasana',
      tagline: 'Supporting blood sugar management',
      about: 'Regular movement improves how sensitive your body is to insulin, and twists and forward ' +
             'folds work the abdomen where the pancreas sits. Consistency matters far more than intensity ' +
             'here - a short daily practice beats a long weekly one.',
      why: [
        'Twists compress and stimulate the abdominal organs.',
        'Steady leg-strengthening work improves glucose uptake by the muscles.',
        'Foot and ankle mobility work matters when circulation is compromised.'
      ],
      safety: [
        'Keep a fast-acting sugar within reach in case of a hypo.',
        'Check your feet before and after practice if you have any neuropathy.',
        'Never adjust medication because a practice is going well - that is your doctor\'s call.'
      ],
      autoFlags: [],
      sessions: [
        {
          id: 'dia_daily', name: 'Daily Practice', level: 2,
          note: 'Aim for most days of the week rather than a long session now and then.',
          steps: [
            { p: 'joint_rotations', sec: 60 }, { p: 'tadasana', sec: 30 },
            { p: 'utkatasana', sec: 25 }, { p: 'virabhadrasana2', sec: 30 },
            { p: 'trikonasana', sec: 30 }, { p: 'marjari_bitilasana', sec: 60 },
            { p: 'bhujangasana', sec: 25 }, { p: 'dhanurasana', sec: 20 },
            { p: 'ardha_matsyendrasana', sec: 30 }, { p: 'paschimottanasana', sec: 40 },
            { p: 'kapalabhati', sec: 60 }, { p: 'anulom_vilom', sec: 120 },
            { p: 'savasana', sec: 150 }
          ]
        },
        {
          id: 'dia_gentle', name: 'Gentle Circulation Practice', level: 1,
          note: 'For stiff or low-energy days, and for anyone with neuropathy.',
          steps: [
            { p: 'joint_rotations', sec: 90 }, { p: 'chair_sit', sec: 45 },
            { p: 'chair_twist', sec: 30 }, { p: 'marjari_bitilasana', sec: 60 },
            { p: 'vajrasana', sec: 90 }, { p: 'janu_sirsasana', sec: 40 },
            { p: 'legs_on_chair', sec: 150 }, { p: 'deep_breathing', sec: 120 }
          ]
        }
      ]
    },

    {
      id: 'hypertension', name: 'High Blood Pressure', hero: 'supta_baddha_konasana',
      tagline: 'Calming practice for raised BP',
      about: 'The practices that help here are the quiet ones. Slow breathing with a long exhale has a ' +
             'direct, measurable effect on blood pressure; strong holds, inversions and forceful breathing ' +
             'do the opposite and are left out.',
      why: [
        'A longer exhale than inhale slows the heart rate directly.',
        'Bhramari and Alternate Nostril Breathing both lower blood pressure reliably.',
        'Restorative poses lower sympathetic activity without any exertion.'
      ],
      safety: [
        'No inversions, no head-below-heart poses, no breath retention, no Kapalabhati or Bhastrika.',
        'Keep taking your medication and keep monitoring.',
        'Come out of any pose that makes the head pound.'
      ],
      autoFlags: ['hypertension'],
      sessions: [
        {
          id: 'bp_calm', name: 'Calming Daily Practice', level: 1,
          note: 'Twice a day if you can - morning and evening.',
          steps: [
            { p: 'sukhasana', sec: 60 }, { p: 'neck_rolls', sec: 45 },
            { p: 'shoulder_rolls', sec: 45 }, { p: 'seated_side_bend', sec: 30 },
            { p: 'marjari_bitilasana', sec: 60 }, { p: 'balasana', sec: 60 },
            { p: 'setu_bandhasana', sec: 30 }, { p: 'supta_baddha_konasana', sec: 150 },
            { p: 'legs_on_chair', sec: 150 }, { p: 'deep_breathing', sec: 150 },
            { p: 'bhramari', sec: 120 }, { p: 'savasana', sec: 180 }
          ]
        },
        {
          id: 'bp_quick', name: 'Quick Pressure Reset', level: 1,
          note: 'Ten minutes, sitting, anywhere.',
          steps: [
            { p: 'sukhasana', sec: 45 }, { p: 'deep_breathing', sec: 150 },
            { p: 'anulom_vilom', sec: 150 }, { p: 'bhramari', sec: 120 },
            { p: 'sheetali', sec: 90 }
          ]
        }
      ]
    },

    {
      id: 'sleep', name: 'Sleep & Anxiety', hero: 'viparita_karani',
      tagline: 'Insomnia, restlessness, worry',
      about: 'Everything in this section is designed to shift you out of alertness. Forward folds and ' +
             'restorative poses held for a long time, a breath with a much longer exhale than inhale, and ' +
             'no effort anywhere.',
      why: [
        'A long exhale is the most direct switch into the parasympathetic state.',
        'Legs Up the Wall is unusually reliable at the end of a long day.',
        'Yoga Nidra gives an overactive mind something to do other than worry.'
      ],
      safety: [
        'Practise in dim light and keep it slow - anything vigorous will wake you up.',
        'Do this in the hour before bed, not straight after a heavy meal.',
        'Persistent insomnia or anxiety deserves proper support as well as this.'
      ],
      autoFlags: [],
      sessions: [
        {
          id: 'sleep_wind', name: 'Wind-Down Before Bed', level: 1,
          note: 'Do it in nightclothes with the lights low. Falling asleep at the end is fine.',
          steps: [
            { p: 'sukhasana', sec: 60 }, { p: 'neck_rolls', sec: 45 },
            { p: 'seated_side_bend', sec: 30 }, { p: 'balasana', sec: 90 },
            { p: 'supta_matsyendrasana', sec: 60 }, { p: 'supta_baddha_konasana', sec: 150 },
            { p: 'viparita_karani', sec: 180 }, { p: 'four_seven_eight', sec: 120 },
            { p: 'yoga_nidra', sec: 300 }
          ]
        },
        {
          id: 'sleep_anxiety', name: 'Settle an Anxious Mind', level: 1,
          note: 'Use it in the moment, not only at bedtime.',
          steps: [
            { p: 'sukhasana', sec: 45 }, { p: 'shoulder_rolls', sec: 45 },
            { p: 'uttanasana', sec: 40 }, { p: 'balasana', sec: 90 },
            { p: 'box_breathing', sec: 160 }, { p: 'bhramari', sec: 150 },
            { p: 'savasana', sec: 180 }
          ]
        },
        {
          id: 'sleep_bed', name: 'In-Bed Practice', level: 1,
          note: 'Every step of this can be done lying in bed with the lights off.',
          steps: [
            { p: 'pawanmuktasana', sec: 45 }, { p: 'supta_matsyendrasana', sec: 60 },
            { p: 'supta_baddha_konasana', sec: 150 }, { p: 'four_seven_eight', sec: 120 },
            { p: 'yoga_nidra', sec: 300 }
          ]
        }
      ]
    },

    {
      id: 'migraine', name: 'Headache & Migraine', hero: 'balasana',
      tagline: 'Tension headache and migraine support',
      about: 'Much of what presents as headache starts in the neck and shoulders. This section releases ' +
             'that, then uses cooling and quietening breath practice. Between attacks it is preventive ' +
             'work; during one, only the gentlest pieces apply.',
      why: [
        'Neck and shoulder release addresses the most common physical trigger.',
        'Bhramari eases tension headache quickly for many people.',
        'Cooling breaths help when the head feels hot and pressured.'
      ],
      safety: [
        'During an active migraine, do only the breathing - in the dark, and nothing more.',
        'Avoid strong inversions and anything that increases pressure in the head.',
        'A sudden severe headache unlike your usual pattern needs urgent medical attention.'
      ],
      autoFlags: [],
      sessions: [
        {
          id: 'mig_prevent', name: 'Between Attacks', level: 1,
          note: 'Regular practice on good days is what reduces the frequency.',
          steps: [
            { p: 'sukhasana', sec: 45 }, { p: 'neck_rolls', sec: 60 },
            { p: 'shoulder_rolls', sec: 60 }, { p: 'gomukhasana', sec: 30 },
            { p: 'marjari_bitilasana', sec: 60 }, { p: 'balasana', sec: 90 },
            { p: 'prasarita', sec: 35 }, { p: 'legs_on_chair', sec: 150 },
            { p: 'anulom_vilom', sec: 150 }, { p: 'savasana', sec: 180 }
          ]
        },
        {
          id: 'mig_acute', name: 'During a Headache', level: 1,
          note: 'Dark room, no movement to speak of. Stop if anything makes it worse.',
          steps: [
            { p: 'balasana', sec: 120 }, { p: 'supta_baddha_konasana', sec: 180 },
            { p: 'bhramari', sec: 150 }, { p: 'sheetali', sec: 90 },
            { p: 'yoga_nidra', sec: 300 }
          ]
        }
      ]
    },

    {
      id: 'desk', name: 'Desk Neck & Shoulders', hero: 'gomukhasana',
      tagline: 'Screen posture, stiff neck, tight upper back',
      about: 'A specific, repetitive problem: head forward, shoulders rounded, chest closed, hips locked ' +
             'at ninety degrees. The counter-movements are equally specific, and most of them can be done ' +
             'without leaving your chair.',
      why: [
        'Chest openers reverse the closing that hours of typing produce.',
        'Twists restore the rotation a chair takes away.',
        'Two minutes every hour beats thirty minutes at the end of the day.'
      ],
      safety: [
        'Never force a stiff neck - small ranges, held longer, work better.',
        'Pins and needles down an arm is a doctor\'s question, not a stretching one.'
      ],
      autoFlags: [],
      sessions: [
        {
          id: 'desk_micro', name: 'Two-Minute Desk Break', level: 1,
          note: 'At your desk, fully clothed, no mat. Set it to repeat hourly.',
          steps: [
            { p: 'chair_sit', sec: 30 }, { p: 'neck_rolls', sec: 45 },
            { p: 'shoulder_rolls', sec: 45 }, { p: 'chair_twist', sec: 30 }
          ]
        },
        {
          id: 'desk_full', name: 'End-of-Day Unwind', level: 1,
          note: 'For after work, when the stiffness has had all day to build.',
          steps: [
            { p: 'shoulder_rolls', sec: 45 }, { p: 'neck_rolls', sec: 45 },
            { p: 'garudasana', sec: 25 }, { p: 'gomukhasana', sec: 30 },
            { p: 'wall_chest_opener', sec: 30 }, { p: 'marjari_bitilasana', sec: 60 },
            { p: 'adho_mukha', sec: 30 }, { p: 'ardha_matsyendrasana', sec: 30 },
            { p: 'supta_matsyendrasana', sec: 45 }, { p: 'legs_on_chair', sec: 150 },
            { p: 'deep_breathing', sec: 120 }
          ]
        },
        {
          id: 'desk_eyes', name: 'Eyes, Neck and Wrists', level: 1,
          note: 'The three things a keyboard costs you.',
          steps: [
            { p: 'chair_sit', sec: 30 }, { p: 'eye_exercises', sec: 120 },
            { p: 'neck_rolls', sec: 60 }, { p: 'joint_rotations', sec: 90 },
            { p: 'deep_breathing', sec: 90 }
          ]
        }
      ]
    },

    {
      id: 'postnatal', name: 'After Birth', hero: 'pelvic_tilts',
      tagline: 'Rebuilding gently after delivery',
      about: 'Recovery is slower than most people expect and the order matters: breath and deep core ' +
             'first, then the pelvic floor, and only then anything that looks like abdominal exercise. ' +
             'Going too fast is the main way this goes wrong.',
      why: [
        'Breathing reconnects the deep core before any strengthening is useful.',
        'Pelvic tilts wake the abdominal wall with no load at all.',
        'Chest and shoulder openers counter hours of feeding and carrying.'
      ],
      safety: [
        'Wait for your postnatal check and your doctor\'s clearance - usually around six weeks, longer after a caesarean.',
        'No crunches, no strong twists and no planks until abdominal separation has been checked.',
        'Any coning or doming down the midline of the belly means stop that movement.',
        'Ligaments stay loose for months, especially if you are breastfeeding.'
      ],
      autoFlags: ['postnatal'],
      sessions: [
        {
          id: 'post_early', name: 'Early Days', level: 1,
          note: 'Once cleared. Almost nothing to it, and that is the point.',
          steps: [
            { p: 'side_lying_rest', sec: 120 }, { p: 'deep_breathing', sec: 150 },
            { p: 'pelvic_tilts', sec: 60 }, { p: 'marjari_bitilasana', sec: 60 },
            { p: 'shoulder_rolls', sec: 45 }, { p: 'neck_rolls', sec: 45 },
            { p: 'legs_on_chair', sec: 150 }
          ]
        },
        {
          id: 'post_rebuild', name: 'Rebuilding Strength', level: 1,
          note: 'When the early sequence feels easy and nothing domes or aches afterwards.',
          steps: [
            { p: 'marjari_bitilasana', sec: 60 }, { p: 'pelvic_tilts', sec: 60 },
            { p: 'setu_bandhasana', sec: 30 }, { p: 'baddha_konasana', sec: 60 },
            { p: 'wall_chest_opener', sec: 30 }, { p: 'virabhadrasana2', sec: 30 },
            { p: 'balasana', sec: 60 }, { p: 'supta_baddha_konasana', sec: 150 },
            { p: 'deep_breathing', sec: 120 }, { p: 'savasana', sec: 150 }
          ]
        }
      ]
    },

    {
      id: 'seniors', name: 'Gentle & Chair Yoga', hero: 'chair_sit',
      tagline: 'Seated and supported practice',
      about: 'Every practice in this app can be adapted to a chair. These sessions are built that way from ' +
             'the start - nothing on the floor, nothing that depends on balance, and nothing that has to ' +
             'be got up from.',
      why: [
        'Joint rotations keep the small joints mobile, which is what preserves independence.',
        'Seated twists and side bends keep the spine moving without any risk of a fall.',
        'Breathing practice works exactly as well sitting in a chair.'
      ],
      safety: [
        'Use a stable chair without wheels, ideally against a wall.',
        'Keep one hand on the chair for any standing work.',
        'Move slowly between positions - a head rush from standing up fast is the real hazard.'
      ],
      autoFlags: [],
      sessions: [
        {
          id: 'sen_chair', name: 'Full Chair Practice', level: 1,
          note: 'Everything from a seated position.',
          steps: [
            { p: 'chair_sit', sec: 45 }, { p: 'joint_rotations', sec: 90 },
            { p: 'neck_rolls', sec: 60 }, { p: 'shoulder_rolls', sec: 60 },
            { p: 'seated_side_bend', sec: 30 }, { p: 'chair_twist', sec: 30 },
            { p: 'chair_fold', sec: 40 }, { p: 'deep_breathing', sec: 120 },
            { p: 'anulom_vilom', sec: 120 }
          ]
        },
        {
          id: 'sen_mobility', name: 'Standing Balance & Mobility', level: 1,
          note: 'Do this beside a wall or a sturdy chair back, always.',
          steps: [
            { p: 'chair_sit', sec: 30 }, { p: 'joint_rotations', sec: 60 },
            { p: 'tadasana', sec: 40 }, { p: 'wall_chest_opener', sec: 30 },
            { p: 'vrikshasana', sec: 30 },
            { p: 'konasana', sec: 25 }, { p: 'chair_fold', sec: 40 },
            { p: 'legs_on_chair', sec: 150 }, { p: 'deep_breathing', sec: 120 }
          ]
        }
      ]
    },

    {
      id: 'eyes', name: 'Eye Strain', hero: 'eye_exercises',
      tagline: 'Screen fatigue, tired eyes',
      about: 'Eye muscles held at one focal distance for hours behave like any other muscle held in one ' +
             'position. Moving them through their range and then resting them in darkness is most of the ' +
             'practice.',
      why: [
        'Deliberate eye movement counters a fixed focal distance.',
        'Palming - cupping warm hands over closed eyes - is the part that gives the most relief.',
        'Neck release matters, because neck tension restricts blood flow to the head.'
      ],
      safety: [
        'Skip after recent eye surgery or injury until you have been cleared.',
        'Trataka is not suitable with glaucoma, or with photosensitive epilepsy.',
        'None of this replaces an eye test.'
      ],
      autoFlags: [],
      sessions: [
        {
          id: 'eye_break', name: 'Screen Break', level: 1,
          note: 'Three minutes. Do it several times a day rather than once.',
          steps: [
            { p: 'chair_sit', sec: 30 }, { p: 'eye_exercises', sec: 120 },
            { p: 'neck_rolls', sec: 45 }
          ]
        },
        {
          id: 'eye_full', name: 'Full Eye Practice', level: 2,
          note: 'Evening, in a dim room.',
          steps: [
            { p: 'sukhasana', sec: 45 }, { p: 'neck_rolls', sec: 60 },
            { p: 'shoulder_rolls', sec: 45 }, { p: 'eye_exercises', sec: 150 },
            { p: 'trataka', sec: 180 }, { p: 'bhramari', sec: 120 },
            { p: 'savasana', sec: 150 }
          ]
        }
      ]
    },

    {
      id: 'immunity', name: 'Immunity', hero: 'simhasana',
      tagline: 'General resilience',
      about: 'There is no posture that prevents illness. What regular practice does do is improve sleep, ' +
             'lower chronic stress and keep circulation moving - and those are the things that actually ' +
             'underpin resilience.',
      why: [
        'Consistent sleep and lower stress are the mechanisms that matter here.',
        'Chest openers and breathing practice keep the airways clear.',
        'Regular gentle movement beats occasional hard effort.'
      ],
      safety: [
        'Do not practise with a fever. Rest instead.',
        'Ease back gradually after an illness rather than picking up where you left off.'
      ],
      autoFlags: [],
      sessions: [
        {
          id: 'imm_daily', name: 'Daily Resilience', level: 2,
          note: 'Morning, on an empty stomach.',
          steps: [
            { p: 'joint_rotations', sec: 60 }, { p: 'urdhva_hastasana', sec: 30 },
            { p: 'virabhadrasana1', sec: 30 }, { p: 'trikonasana', sec: 30 },
            { p: 'marjari_bitilasana', sec: 60 }, { p: 'bhujangasana', sec: 25 },
            { p: 'setu_bandhasana', sec: 30 }, { p: 'simhasana', sec: 40 },
            { p: 'bhastrika', sec: 45 }, { p: 'anulom_vilom', sec: 150 },
            { p: 'savasana', sec: 180 }
          ]
        },
        {
          id: 'imm_recover', name: 'Recovering After Illness', level: 1,
          note: 'Once the fever has gone and only as far as energy allows.',
          steps: [
            { p: 'sukhasana', sec: 60 }, { p: 'joint_rotations', sec: 60 },
            { p: 'shoulder_rolls', sec: 45 }, { p: 'marjari_bitilasana', sec: 60 },
            { p: 'balasana', sec: 60 }, { p: 'legs_on_chair', sec: 150 },
            { p: 'deep_breathing', sec: 150 }, { p: 'yoga_nidra', sec: 300 }
          ]
        }
      ]
    },

    {
      id: 'weight', name: 'Weight Management', hero: 'virabhadrasana2',
      tagline: 'Strength, stamina and appetite',
      about: 'Yoga is not the most efficient way to burn calories, and pretending otherwise sets people up ' +
             'to quit. What it does well is build strength and stamina, improve how you eat by improving ' +
             'sleep and stress, and be sustainable enough that you keep doing it.',
      why: [
        'Held standing poses build muscle, and muscle is what raises resting metabolism.',
        'Linking poses to the breath raises the heart rate without impact on the joints.',
        'Lower stress and better sleep both change appetite regulation.'
      ],
      safety: [
        'Build up gradually - too much too soon is the fastest route to stopping.',
        'Sustainable frequency beats occasional intensity every time.'
      ],
      autoFlags: [],
      sessions: [
        {
          id: 'wt_strength', name: 'Strength & Stamina', level: 2,
          note: 'Hold each standing pose to the end of the timer, breathing steadily.',
          steps: [
            { p: 'joint_rotations', sec: 45 }, { p: 'tadasana', sec: 30 },
            { p: 'utkatasana', sec: 30 }, { p: 'virabhadrasana1', sec: 30 },
            { p: 'virabhadrasana2', sec: 30 }, { p: 'parsvakonasana', sec: 30 },
            { p: 'trikonasana', sec: 30 }, { p: 'adho_mukha', sec: 30 },
            { p: 'bhujangasana', sec: 25 }, { p: 'salabhasana', sec: 20 },
            { p: 'uttanpadasana', sec: 20 }, { p: 'kapalabhati', sec: 60 },
            { p: 'savasana', sec: 180 }
          ]
        },
        {
          id: 'wt_start', name: 'Starting Out', level: 1,
          note: 'The version to begin with if it has been a while.',
          steps: [
            { p: 'joint_rotations', sec: 60 }, { p: 'tadasana', sec: 30 },
            { p: 'urdhva_hastasana', sec: 30 }, { p: 'konasana', sec: 25 },
            { p: 'utkatasana', sec: 20 }, { p: 'virabhadrasana2', sec: 30 },
            { p: 'marjari_bitilasana', sec: 60 }, { p: 'bhujangasana', sec: 25 },
            { p: 'pawanmuktasana', sec: 40 }, { p: 'deep_breathing', sec: 120 },
            { p: 'savasana', sec: 150 }
          ]
        }
      ]
    },

    {
      id: 'joints', name: 'Joints & Mobility', hero: 'joint_rotations',
      tagline: 'Stiffness, arthritis, knees',
      about: 'Stiff and arthritic joints do better with frequent gentle movement through range than with ' +
             'strong stretching. The traditional joint-rotation series was built for exactly this, and it ' +
             'is safe to do every day.',
      why: [
        'Moving a joint through its range is what keeps it lubricated.',
        'Strengthening around a joint takes load off the joint itself.',
        'Little and often works far better than occasional and hard.'
      ],
      safety: [
        'Warmth first - these practices go better after a warm shower.',
        'Discomfort during movement is usually fine; sharp pain is not.',
        'Never load a joint that is actively inflamed and swollen.'
      ],
      autoFlags: [],
      sessions: [
        {
          id: 'jnt_daily', name: 'Daily Joint Mobility', level: 1,
          note: 'Safe every day, and worth doing every day.',
          steps: [
            { p: 'chair_sit', sec: 30 }, { p: 'joint_rotations', sec: 120 },
            { p: 'neck_rolls', sec: 60 }, { p: 'shoulder_rolls', sec: 60 },
            { p: 'seated_side_bend', sec: 30 }, { p: 'chair_twist', sec: 30 },
            { p: 'marjari_bitilasana', sec: 60 }, { p: 'deep_breathing', sec: 120 }
          ]
        },
        {
          id: 'jnt_knees', name: 'Knees & Hips', level: 1,
          note: 'Nothing here puts weight through a bent knee.',
          steps: [
            { p: 'joint_rotations', sec: 90 }, { p: 'dandasana', sec: 40 },
            { p: 'baddha_konasana', sec: 60 }, { p: 'upavistha_konasana', sec: 45 },
            { p: 'pelvic_tilts', sec: 60 }, { p: 'setu_bandhasana', sec: 30 },
            { p: 'supta_matsyendrasana', sec: 45 }, { p: 'legs_on_chair', sec: 150 },
            { p: 'savasana', sec: 150 }
          ]
        }
      ]
    }
  ];

  YG.CONDITION_BY_ID = {};
  for (var i = 0; i < YG.CONDITIONS.length; i++) {
    YG.CONDITION_BY_ID[YG.CONDITIONS[i].id] = YG.CONDITIONS[i];
  }
})(window.YG = window.YG || {});
