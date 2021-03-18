/*&silent*/
import groovy.transform.Field

import com.google.gson.Gson
import com.google.gson.GsonBuilder

import groovy.time.TimeCategory

@Field Date currentDate = new Date()
@Field Date periodDate = use(TimeCategory) { currentDate - 1.minute }

def getHost() {
    return api.rest.getBaseUrl() - 'https://' - 'http://' - '/sd' - '/operator'
}

def serviceCallByStates() {
    String hqlServiceCallsByStates = '''
SELECT sc.state, count(sc)
FROM serviceCall sc
GROUP BY sc.state
'''
    return api.db.query(hqlServiceCallsByStates)
            .list().collect { row ->
        return [
                'state': row[0],
                'count': row[1]
        ]
    }
}

def periodComments() {
    String hqlPeriodComments = '''
SELECT comm.source
FROM comment comm
WHERE comm.creationDate between :periodDate and :currentDate
AND comm.author is not null
'''

    def comments = api.db.query(hqlPeriodComments)
            .set('periodDate', periodDate)
            .set('currentDate', currentDate)
            .list()
            .collect { row ->
                return row.tokenize('$')[0]
            }
            .groupBy()
            .collect { return ["class": it.key, "count": it.value.size()] }
}

def periodServiceCalls() {
    String hqlPeriodServiceCalls = '''
SELECT 'serviceCall$'||sc.id, 'employee$'||sc.author.id, sc.requestDate, sc.registrationDate, sc.wayAddressing.code
FROM serviceCall sc
WHERE sc.creationDate between :periodDate and :currentDate
'''

    api.db.query(hqlPeriodServiceCalls)
            .set('periodDate', periodDate)
            .set('currentDate', currentDate)
            .list().collect { row ->
        return [
                'UUID'            : row[0],
                'author'          : row[1],
                'requestDate'     : row[2].getTime(),
                'registrationDate': row[3].getTime(),
                'registrationTime': row[3].getTime() - row[2].getTime(),
                'wayAddressing'   : row[4]
        ]
    }
}

def periodAdminActions() {
    String hqlPeriodAdminLogs = '''
SELECT 'adminLogRecord$'||log.id, log.authorLogin, log.category
FROM adminLogRecord log
WHERE log.actionDate between :periodDate and :currentDate
AND log.authorLogin is not 'naumen'
'''

    api.db.query(hqlPeriodAdminLogs)
            .set('periodDate', periodDate)
            .set('currentDate', currentDate)
            .list().collect { row ->
        return [
                'UUID'    : row[0],
                'author'  : row[1],
                'category': row[2]
        ]
    }
}

def licenseUsage() {
    api.employee.getLicensesUsage().collect {
        return ["license": it.key,
                "count"  : it.value]
    }
}

def business_metrics = [
        'host'                       : getHost(),
        'date'                       : currentDate.getTime(),

        'serviceCalls_for_the_period': periodServiceCalls(),
        'serviceCalls_per_states'    : serviceCallByStates(),
        'comments_for_the_period'    : periodComments(),
        'license_usage_per_user'     : licenseUsage(),
        'admin_logs_for_the_period'  : periodAdminActions()
]

Gson gson = new GsonBuilder().setPrettyPrinting().create()
return gson.toJson(business_metrics)